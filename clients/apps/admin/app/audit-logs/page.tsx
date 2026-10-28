import {
  Alert,
  Button,
  Card,
  formatDateTime,
  Input,
  Label,
  PAGE_SIZE,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { adminClient } from "@/lib/admin-client";
import { ADMIN_SERVICES } from "@/lib/admin-services";
import {
  ACTION_LABELS,
  actionLabel,
  auditLogQuery,
  targetLabel,
  type AuditLogFilters,
} from "@/lib/audit-log";
import { fanOut } from "@/lib/fan-out";
import { mergeByTime } from "@/lib/merged-page";
import { requireSession } from "@/lib/session";

const selectClass = "h-10 w-full rounded-md border border-input bg-card px-3 text-sm";

/**
 * 감사 로그(ADM-4, NFR-8, ADR 0023). 관리자 작업 기록은 서비스 스키마마다 있어서 대상 서비스에 동시에 묻고 시간순으로 합친다. 기간, 서비스,
 * 작업, 작업한 사람으로 거르고 커서로 다음 쪽을 본다. 응답하지 않은 서비스가 있으면 나머지로 보이고 빠진 서비스를 알린다.
 */
export default async function AuditLogsPage({ searchParams }: PageProps<"/audit-logs">) {
  await requireSession("/audit-logs");
  const query = auditLogQuery(await searchParams);
  return (
    <>
      <PageHeader
        title="감사 로그"
        description="관리자 작업을 누가, 언제, 무엇에 했는지 최근 순으로 보입니다. 기록은 지우거나 고치지 않습니다."
      />
      <FilterForm filters={query.filters} />
      {query.error ? (
        <Alert variant="destructive">{query.error}</Alert>
      ) : (
        <AuditLogList query={query} />
      )}
    </>
  );
}

function FilterForm({ filters }: { filters: AuditLogFilters }) {
  return (
    <form
      method="get"
      action="/audit-logs"
      className="mb-4 grid gap-3 sm:grid-cols-[repeat(4,minmax(0,1fr))_minmax(0,1.5fr)] sm:items-end"
    >
      <div className="grid gap-1">
        <Label htmlFor="from">시작일</Label>
        <Input id="from" name="from" type="date" defaultValue={filters.from} />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="to">종료일</Label>
        <Input id="to" name="to" type="date" defaultValue={filters.to} />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="service">서비스</Label>
        <select
          id="service"
          name="service"
          defaultValue={filters.service ?? ""}
          className={selectClass}
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
        <Label htmlFor="action">작업</Label>
        <select
          id="action"
          name="action"
          defaultValue={filters.action ?? ""}
          className={selectClass}
        >
          <option value="">전체</option>
          {Object.entries(ACTION_LABELS).map(([action, label]) => (
            <option key={action} value={action}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1">
        <Label htmlFor="actorId">작업한 사람(ID)</Label>
        <Input id="actorId" name="actorId" defaultValue={filters.actorId} maxLength={100} />
      </div>
      <div className="flex gap-2 sm:col-span-full">
        <Button type="submit">조회</Button>
        <Button asChild variant="outline">
          <Link href="/audit-logs">조건 지우기</Link>
        </Button>
      </div>
    </form>
  );
}

async function AuditLogList({ query }: { query: ReturnType<typeof auditLogQuery> }) {
  const { results, failed } = await fanOut(query.services, async (service, signal) => {
    const client = await adminClient(service);
    const { data, response } = await client.GET("/v1/admin/audit-logs", {
      params: { query: { ...query.query, size: PAGE_SIZE } },
      signal,
    });
    return response.ok && data ? data : null;
  });
  if (results.length === 0) {
    return <Alert variant="destructive">감사 로그를 불러오지 못했습니다.</Alert>;
  }
  const page = mergeByTime(
    results.map(({ service, value }) => (value.items ?? []).map((item) => ({ service, item }))),
    results.map(({ value }) => Boolean(value.nextCursor)),
    PAGE_SIZE,
    (item) => item.occurredAt,
  );
  const first = new URLSearchParams(
    Object.entries(query.filters).filter((entry): entry is [string, string] => Boolean(entry[1])),
  );
  const next = new URLSearchParams(first);
  next.set("before", page.nextCursor ?? "");
  return (
    <>
      {failed.length > 0 ? (
        <Alert variant="destructive" className="mb-4">
          응답하지 않은 서비스가 있어 그 기록은 빠졌습니다: {failed.join(", ")}
        </Alert>
      ) : null}
      {page.rows.length === 0 ? (
        <Alert>감사 로그가 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>시각</TableHead>
                <TableHead>서비스</TableHead>
                <TableHead>작업</TableHead>
                <TableHead>대상</TableHead>
                <TableHead>작업한 사람</TableHead>
                <TableHead>부가 설명</TableHead>
                <TableHead>trace id</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {page.rows.map(({ service, item }) => (
                <TableRow key={`${service}-${item.id}`}>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.occurredAt)}
                  </TableCell>
                  <TableCell>{service}</TableCell>
                  <TableCell className="whitespace-nowrap">{actionLabel(item.action)}</TableCell>
                  <TableCell>
                    <span className="whitespace-nowrap">{targetLabel(item.targetType)}</span>
                    <span className="block font-mono text-xs break-all text-muted-foreground">
                      {item.targetId}
                    </span>
                  </TableCell>
                  <TableCell className="font-mono text-xs break-all">{item.actorId}</TableCell>
                  <TableCell className="text-sm break-all">{item.detail ?? "-"}</TableCell>
                  <TableCell className="font-mono text-xs break-all">
                    {item.traceId ?? "-"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <nav aria-label="쪽" className="mt-4 flex justify-end gap-2">
        {query.query.before ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/audit-logs?${first.toString()}`}>처음으로</Link>
          </Button>
        ) : null}
        {page.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/audit-logs?${next.toString()}`}>다음</Link>
          </Button>
        ) : null}
      </nav>
    </>
  );
}
