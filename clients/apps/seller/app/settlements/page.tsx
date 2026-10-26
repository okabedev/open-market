import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatDateTime,
  formatPrice,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";
import { periodLabel, settlementStatusLabel } from "@/lib/settlement";
import { SettlementAmounts } from "./settlement-parts";

const PAGE_SIZE = 20;

/**
 * 정산(STL-2). 위는 아직 확정되지 않은 구매확정 건의 정산 예정 금액, 아래는 확정된 정산(기간 최근 순 커서 목록, `?cursor=`)이다. 정산은
 * 정산 기간이 끝나고 배치가 확정하며, 관리자가 지급하면 지급 완료가 된다.
 */
export default async function SettlementsPage({ searchParams }: PageProps<"/settlements">) {
  await requireSeller("/settlements");
  const { cursor } = await searchParams;
  const settlement = await bff.serviceClient("settlement");
  const [pending, confirmed] = await Promise.all([
    settlement
      .GET("/v1/seller/settlement-items/pending/summary")
      .catch(() => ({ data: undefined })),
    settlement
      .GET("/v1/seller/settlements", {
        params: { query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) } },
      })
      .catch(() => ({ data: undefined })),
  ]);
  const items = confirmed.data?.items ?? [];
  return (
    <>
      <PageHeader
        title="정산"
        description="구매확정한 주문을 정산 기간마다 모아 확정합니다. 정산 금액은 판매 금액에서 수수료와 판매자 쿠폰 할인을 뺀 금액입니다."
      />
      <div className="grid gap-6">
        <Card>
          <CardHeader className="flex-row items-center justify-between gap-4">
            <CardTitle>정산 예정</CardTitle>
            <Button asChild variant="outline" size="sm">
              <Link href="/settlements/pending">정산 예정 내역</Link>
            </Button>
          </CardHeader>
          <CardContent className="grid max-w-md gap-2">
            {!pending.data ? (
              <Alert variant="destructive">정산 예정 금액을 불러오지 못했습니다.</Alert>
            ) : (
              <>
                <p className="text-sm text-muted-foreground">
                  아직 확정되지 않은 구매확정{" "}
                  {(pending.data.itemCount ?? 0).toLocaleString("ko-KR")}
                  건입니다.
                </p>
                <SettlementAmounts
                  amounts={pending.data}
                  total={pending.data.settlementAmount}
                  totalLabel="정산 예정 금액"
                />
              </>
            )}
          </CardContent>
        </Card>

        <section aria-labelledby="confirmed-heading" className="grid gap-3">
          <h2 id="confirmed-heading" className="text-lg font-semibold">
            확정된 정산
          </h2>
          {!confirmed.data ? (
            <Alert variant="destructive">정산을 불러오지 못했습니다.</Alert>
          ) : items.length === 0 ? (
            <Alert>
              {typeof cursor === "string" ? "더 볼 정산이 없습니다." : "확정된 정산이 없습니다."}
            </Alert>
          ) : (
            <Card>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>정산 기간</TableHead>
                    <TableHead className="text-right">건수</TableHead>
                    <TableHead className="text-right">판매 금액</TableHead>
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
                      <TableCell className="text-right">
                        {(item.itemCount ?? 0).toLocaleString("ko-KR")}
                      </TableCell>
                      <TableCell className="text-right whitespace-nowrap">
                        {formatPrice(item.salesAmount)}
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
          <div className="flex justify-between">
            {typeof cursor === "string" ? (
              <Button asChild variant="outline" size="sm">
                <Link href="/settlements">처음으로</Link>
              </Button>
            ) : (
              <span />
            )}
            {confirmed.data?.nextCursor ? (
              <Button asChild variant="outline" size="sm">
                <Link href={`/settlements?cursor=${encodeURIComponent(confirmed.data.nextCursor)}`}>
                  다음
                </Link>
              </Button>
            ) : null}
          </div>
        </section>
      </div>
    </>
  );
}
