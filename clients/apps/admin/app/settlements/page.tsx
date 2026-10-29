import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmActionForm,
  formatDateTime,
  formatPrice,
  listQuery,
  PAGE_SIZE,
  PageHeader,
  Pager,
  periodLabel,
  settlementStatusLabel,
  StatusTabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { storeNames } from "@/lib/store-names";
import { runSettlement } from "./actions";

const STATUSES = ["CONFIRMED", "PAID_OUT"] as const;

/**
 * 정산 관리(STL-2, STL-3, ADM-3). 확정된 정산을 최근 확정 순, 쪽 번호로 보인다. 상태(지급 대기, 지급 완료)와 판매자로 거른다. 지급은
 * 정산 상세에서 판매자 정산 계좌를 보고 한다. 정산 작업을 바로 돌릴 수 있다(다시 돌려도 결과가 같다). settlement는 상호를 갖지 않아
 * 쪽마다 member에 상호를 한 번 묻는다. 닿지 못하면 판매자 ID를 보인다. 계좌는 상세가 보인다.
 */
export default async function SettlementsPage({ searchParams }: PageProps<"/settlements">) {
  await requireSession("/settlements");
  const params = await searchParams;
  const { status, page } = listQuery(params, STATUSES, undefined);
  const sellerParam = z.uuid().safeParse(params.sellerId);
  const sellerId = sellerParam.success ? sellerParam.data : undefined;
  const confirmed = typeof params.confirmed === "string" ? Number(params.confirmed) : NaN;
  const filters = sellerId ? { sellerId } : {};
  const settlement = await bff.serviceClient("settlement");
  const { data, response } = await settlement
    .GET("/v1/admin/settlements", {
      params: { query: { ...filters, ...(status ? { status } : {}), page, size: PAGE_SIZE } },
    })
    .catch(() => ({ data: undefined, response: undefined }));
  const items = data?.items ?? [];
  const names = await storeNames([sellerId, ...items.map((item) => item.sellerId)]);
  return (
    <>
      <PageHeader
        title="정산"
        description="정산 기간이 끝나면 정산 작업이 판매자마다 확정합니다. 지급은 실제 송금 뒤 지급 완료로 기록합니다."
        actions={
          <ConfirmActionForm
            action={runSettlement}
            hidden={{}}
            title="정산 작업을 지금 돌릴까요?"
            description="끝난 기간 중 아직 확정하지 않은 판매자·기간을 확정합니다. 이미 확정한 정산은 바뀌지 않습니다."
            confirmText="지금 실행"
            variant="outline"
          >
            정산 작업 지금 실행
          </ConfirmActionForm>
        }
      />
      {params.notice === "settlement-run" && Number.isInteger(confirmed) ? (
        <Alert variant="success" className="mb-4">
          정산 작업을 돌렸습니다. 새로 확정한 정산 {confirmed.toLocaleString("ko-KR")}건.
        </Alert>
      ) : null}
      {sellerId ? (
        <p className="mb-2 text-sm text-muted-foreground">
          판매자 {names.get(sellerId) ?? sellerId}의 정산만 봅니다.{" "}
          <Link
            href={status ? `/settlements?status=${status}` : "/settlements"}
            className="underline"
          >
            판매자 필터 해제
          </Link>
        </p>
      ) : null}
      <StatusTabs
        basePath="/settlements"
        current={status}
        query={filters}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "CONFIRMED", label: "지급 대기" },
          { status: "PAID_OUT", label: "지급 완료" },
        ]}
      />
      {!response?.ok || !data ? (
        <Alert variant="destructive">정산을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>정산이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>정산 기간</TableHead>
                <TableHead>판매자</TableHead>
                <TableHead className="text-right">건수</TableHead>
                <TableHead className="text-right">정산 금액</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>확정·지급</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.settlementId}>
                  <TableCell>
                    <Link
                      href={`/settlements/${item.settlementId}`}
                      className="font-medium hover:underline"
                    >
                      {item.periodStart && item.periodEnd
                        ? periodLabel(item.periodStart, item.periodEnd)
                        : "-"}
                    </Link>
                  </TableCell>
                  <TableCell>
                    {item.sellerId ? (
                      <Link
                        href={`/settlements?sellerId=${item.sellerId}`}
                        className="hover:underline"
                        title={item.sellerId}
                      >
                        {names.get(item.sellerId) ?? (
                          <span className="font-mono text-xs">{item.sellerId.slice(0, 8)}</span>
                        )}
                      </Link>
                    ) : null}
                  </TableCell>
                  <TableCell className="text-right">
                    {(item.itemCount ?? 0).toLocaleString("ko-KR")}
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap font-medium">
                    {formatPrice(item.amount)}
                  </TableCell>
                  <TableCell>
                    {item.status ? (
                      <Badge variant={item.status === "PAID_OUT" ? "success" : "secondary"}>
                        {settlementStatusLabel(item.status)}
                      </Badge>
                    ) : null}
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.paidOutAt ?? item.confirmedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath="/settlements"
          status={status}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
          query={filters}
        />
      ) : null}
      <p className="mt-4 text-sm">
        <Button asChild variant="link" className="px-0">
          <Link href="/commission-rates">수수료율 관리</Link>
        </Button>
      </p>
    </>
  );
}
