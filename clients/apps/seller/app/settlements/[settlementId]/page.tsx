import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  formatDateTime,
  PageHeader,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";
import { periodLabel, settlementStatusLabel } from "@/lib/settlement";
import { SettlementAmounts, SettlementItems } from "../settlement-parts";

const PAGE_SIZE = 20;

/**
 * 정산 한 건과 내역(STL-2). 정산 계좌는 settlement에 없어 member의 판매자 정보(가린 계좌 번호)를 함께 보인다. 정산 알림(확정, 지급)이
 * 이 화면으로 온다. 다른 판매자의 정산이면 404다.
 */
export default async function SettlementPage({
  params,
  searchParams,
}: PageProps<"/settlements/[settlementId]">) {
  const { settlementId } = await params;
  const { cursor } = await searchParams;
  await requireSeller(`/settlements/${settlementId}`);
  const [settlement, member] = await Promise.all([
    bff.serviceClient("settlement"),
    bff.serviceClient("member"),
  ]);
  const [{ data, response }, items, seller] = await Promise.all([
    settlement.GET("/v1/seller/settlements/{settlementId}", {
      params: { path: { settlementId } },
    }),
    settlement
      .GET("/v1/seller/settlements/{settlementId}/items", {
        params: {
          path: { settlementId },
          query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) },
        },
      })
      .catch(() => ({ data: undefined })),
    member.GET("/v1/seller/me").catch(() => ({ data: undefined })),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">정산을 불러오지 못했습니다.</Alert>;
  }
  const account = seller.data;
  return (
    <>
      <PageHeader
        title={`${data.periodStart && data.periodEnd ? periodLabel(data.periodStart, data.periodEnd) : ""} 정산`}
        description={`구매확정 ${(data.itemCount ?? 0).toLocaleString("ko-KR")}건 · ${formatDateTime(data.confirmedAt)} 확정`}
        actions={
          <Button asChild variant="outline">
            <Link href="/settlements">정산</Link>
          </Button>
        }
      />
      <div className="grid gap-6">
        <Card>
          <CardContent className="grid max-w-md gap-4 pt-6 text-sm">
            <p className="flex flex-wrap items-center gap-2">
              {data.status ? (
                <Badge variant={data.status === "PAID_OUT" ? "success" : "secondary"}>
                  {settlementStatusLabel(data.status)}
                </Badge>
              ) : null}
              {data.paidOutAt ? (
                <span>{formatDateTime(data.paidOutAt)} 지급</span>
              ) : (
                <span className="text-muted-foreground">관리자가 지급하면 지급 완료가 됩니다.</span>
              )}
            </p>
            <SettlementAmounts amounts={data} total={data.amount} totalLabel="정산 금액" />
            {account ? (
              <p className="text-muted-foreground">
                정산 계좌 {account.bankName} {account.accountNumberMasked} ({account.accountHolder})
              </p>
            ) : null}
          </CardContent>
        </Card>
        {!items.data ? (
          <Alert variant="destructive">정산 내역을 불러오지 못했습니다.</Alert>
        ) : (
          <SettlementItems
            items={items.data.items ?? []}
            basePath={`/settlements/${settlementId}`}
            cursor={typeof cursor === "string" ? cursor : undefined}
            nextCursor={items.data.nextCursor}
          />
        )}
      </div>
    </>
  );
}
