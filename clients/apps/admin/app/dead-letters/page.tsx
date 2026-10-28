import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmActionForm,
  formatDateTime,
  Input,
  Label,
  PAGE_SIZE,
  PageHeader,
  StatusTabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { ReasonForm } from "@/components/reason-form";
import { adminClient } from "@/lib/admin-client";
import { ADMIN_SERVICES } from "@/lib/admin-services";
import {
  DEAD_LETTER_STATUS_LABELS,
  DEAD_LETTER_TABS,
  deadLetterQuery,
  sameEventCounts,
  shortClassName,
  type DeadLetterFilters,
  type DeadLetterStatus,
  type DeadLetterTab,
} from "@/lib/dead-letter";
import { fanOut } from "@/lib/fan-out";
import { mergeByTime } from "@/lib/merged-page";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { discardDeadLetter, retryDeadLetter } from "./actions";

/**
 * DLT 메시지(ADM-5, ADR 0022·0023). 리스너가 처리하지 못한 메시지는 소비한 서비스가 자기 스키마에 모은다. 대상 서비스에 동시에 묻고 쌓인
 * 시각 최근 순으로 합친다. 처리 대기 메시지는 원인을 고친 뒤 다시 보내거나(실패한 그룹만 처리한다) 사유를 남겨 버린다. 페이로드와 예외
 * 메시지는 개인정보가 있을 수 있어 보이지 않는다. 원인은 trace id로 로그와 트레이스에서 찾는다.
 */
export default async function DeadLettersPage({ searchParams }: PageProps<"/dead-letters">) {
  await requireSession("/dead-letters");
  const params = await searchParams;
  const query = deadLetterQuery(params);
  const notice = noticeMessage(params.notice);
  return (
    <>
      <PageHeader
        title="DLT 메시지"
        description="재시도해도 처리하지 못한 이벤트입니다. 원인을 trace id로 찾아 고친 뒤 다시 보내거나, 처리할 필요가 없으면 사유를 남겨 버립니다."
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <FilterForm status={query.status} filters={query.filters} />
      <StatusTabs
        basePath="/dead-letters"
        current={query.status === "PENDING" ? undefined : query.status}
        query={query.filters}
        tabs={DEAD_LETTER_TABS.map((status) => ({
          status: status === "PENDING" ? undefined : status,
          label: DEAD_LETTER_STATUS_LABELS[status],
        }))}
      />
      <DeadLetterList query={query} />
    </>
  );
}

function FilterForm({ status, filters }: { status: DeadLetterTab; filters: DeadLetterFilters }) {
  return (
    <form
      method="get"
      action="/dead-letters"
      className="mb-4 grid gap-3 sm:grid-cols-[12rem_minmax(0,20rem)_auto] sm:items-end"
    >
      {status === "PENDING" ? null : <input type="hidden" name="status" value={status} />}
      <div className="grid gap-1">
        <Label htmlFor="service">서비스</Label>
        <select
          id="service"
          name="service"
          defaultValue={filters.service ?? ""}
          className="h-10 w-full rounded-md border border-input bg-card px-3 text-sm"
        >
          <option value="">전체</option>
          {ADMIN_SERVICES.map((service) => (
            <option key={service} value={service}>
              {service}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1">
        <Label htmlFor="topic">원래 토픽</Label>
        <Input id="topic" name="topic" defaultValue={filters.topic} placeholder="예: order.order" />
      </div>
      <div className="flex gap-2">
        <Button type="submit">조회</Button>
        <Button asChild variant="outline">
          <Link href="/dead-letters">조건 지우기</Link>
        </Button>
      </div>
    </form>
  );
}

function searchOf(values: Record<string, string | undefined>): URLSearchParams {
  return new URLSearchParams(
    Object.entries(values).filter((entry): entry is [string, string] => Boolean(entry[1])),
  );
}

async function DeadLetterList({ query }: { query: ReturnType<typeof deadLetterQuery> }) {
  const { results, failed } = await fanOut(query.services, async (service, signal) => {
    const client = await adminClient(service);
    const { data, response } = await client.GET("/v1/admin/dead-letters", {
      params: { query: { ...query.query, size: PAGE_SIZE } },
      signal,
    });
    return response.ok && data ? data : null;
  });
  if (results.length === 0) {
    return <Alert variant="destructive">DLT 메시지를 불러오지 못했습니다.</Alert>;
  }
  const page = mergeByTime(
    results.map(({ service, value }) => (value.items ?? []).map((item) => ({ service, item }))),
    results.map(({ value }) => Boolean(value.nextCursor)),
    PAGE_SIZE,
    (item) => item.deadLetteredAt,
  );
  const sameEvent = sameEventCounts(page.rows);
  const status = query.status === "PENDING" ? undefined : query.status;
  const first = searchOf({ ...query.filters, status });
  const current = searchOf({ ...query.filters, status, before: query.query.before });
  const returnTo = `/dead-letters?${current.toString()}`;
  return (
    <>
      {failed.length > 0 ? (
        <Alert variant="destructive" className="mb-4">
          응답하지 않은 서비스가 있어 그 메시지는 빠졌습니다: {failed.join(", ")}
        </Alert>
      ) : null}
      {page.rows.length === 0 ? (
        <Alert>{DEAD_LETTER_STATUS_LABELS[query.status]} 메시지가 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>쌓인 시각</TableHead>
                <TableHead>서비스·그룹</TableHead>
                <TableHead>이벤트</TableHead>
                <TableHead>원래 위치</TableHead>
                <TableHead>예외</TableHead>
                <TableHead>trace id</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {page.rows.map(({ service, item }) => {
                const repeats = item.eventId ? (sameEvent.get(item.eventId) ?? 0) : 0;
                return (
                  <TableRow key={`${service}-${item.id}`}>
                    <TableCell className="whitespace-nowrap">
                      {formatDateTime(item.deadLetteredAt)}
                    </TableCell>
                    <TableCell>
                      <span className="block">{service}</span>
                      <span className="block font-mono text-xs text-muted-foreground">
                        {item.consumerGroup}
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className="block font-medium">{item.eventType ?? "-"}</span>
                      <span className="block font-mono text-xs break-all text-muted-foreground">
                        {item.eventId}
                      </span>
                      {repeats > 1 ? (
                        <Badge variant="outline" className="mt-1">
                          같은 이벤트 {repeats}건
                        </Badge>
                      ) : null}
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {item.originalTopic}
                      <span className="block text-muted-foreground">
                        파티션 {item.originalPartition} · 오프셋 {item.originalOffset}
                      </span>
                    </TableCell>
                    <TableCell className="font-mono text-xs" title={item.exceptionClass}>
                      {shortClassName(item.exceptionClass)}
                    </TableCell>
                    <TableCell className="font-mono text-xs break-all">
                      {item.traceId ?? "-"}
                    </TableCell>
                    <TableCell className="min-w-56">
                      {item.status === "PENDING" ? (
                        <div className="grid gap-2">
                          <ConfirmActionForm
                            action={retryDeadLetter}
                            hidden={{ service, id: item.id ?? "", returnTo }}
                            title="메시지를 다시 보낼까요?"
                            description={`${item.originalTopic} 파티션 ${item.originalPartition}로 다시 보냅니다. ${item.consumerGroup} 그룹만 처리합니다. 원인을 고쳤는지 먼저 확인해 주세요.`}
                            confirmText="다시 보내기"
                          >
                            다시 보내기
                          </ConfirmActionForm>
                          <details>
                            <summary className="cursor-pointer text-sm text-muted-foreground">
                              버리기
                            </summary>
                            <div className="pt-2">
                              <ReasonForm
                                action={discardDeadLetter}
                                hidden={{ service, id: item.id ?? "", returnTo }}
                                label="버리는 사유"
                                submitText="버리기"
                              />
                            </div>
                          </details>
                        </div>
                      ) : (
                        <div className="text-sm">
                          <span className="block">
                            {DEAD_LETTER_STATUS_LABELS[item.status as DeadLetterStatus] ??
                              item.status}{" "}
                            · {formatDateTime(item.resolvedAt)}
                          </span>
                          <span className="block font-mono text-xs break-all text-muted-foreground">
                            {item.resolvedBy}
                          </span>
                          {item.discardReason ? (
                            <span className="block break-all">{item.discardReason}</span>
                          ) : null}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>
      )}
      <nav aria-label="쪽" className="mt-4 flex justify-end gap-2">
        {query.query.before ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/dead-letters?${first.toString()}`}>처음으로</Link>
          </Button>
        ) : null}
        {page.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link
              href={`/dead-letters?${searchOf({ ...query.filters, status, before: page.nextCursor }).toString()}`}
            >
              다음
            </Link>
          </Button>
        ) : null}
      </nav>
    </>
  );
}
