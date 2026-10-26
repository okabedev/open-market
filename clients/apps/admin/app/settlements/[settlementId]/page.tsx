import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ConfirmActionForm,
  formatDateTime,
  formatPrice,
  PageHeader,
  periodLabel,
  SettlementAmounts,
  SettlementItems,
  settlementStatusLabel,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { payOut } from "../actions";

const PAGE_SIZE = 20;

/**
 * 정산 한 건과 지급(STL-3, ADM-3). 정산 계좌는 settlement에 없어 member 관리자 API(`/v1/admin/sellers/{sellerId}`, 대표자·예금주·계좌
 * 번호를 가린 값)로 함께 보인다. 지급 대기면 확인한 뒤 지급 완료로 기록한다. 내역은 커서(`?cursor=`)다.
 */
export default async function SettlementPage({
  params,
  searchParams,
}: PageProps<"/settlements/[settlementId]">) {
  const { settlementId } = await params;
  const query = await searchParams;
  const cursor = typeof query.cursor === "string" ? query.cursor : undefined;
  await requireSession(`/settlements/${settlementId}`);
  const [settlement, member] = await Promise.all([
    bff.serviceClient("settlement"),
    bff.serviceClient("member"),
  ]);
  const [{ data, response }, items] = await Promise.all([
    settlement.GET("/v1/admin/settlements/{settlementId}", {
      params: { path: { settlementId } },
    }),
    settlement
      .GET("/v1/admin/settlements/{settlementId}/items", {
        params: {
          path: { settlementId },
          query: { size: PAGE_SIZE, ...(cursor ? { cursor } : {}) },
        },
      })
      .catch(() => ({ data: undefined })),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">정산을 불러오지 못했습니다.</Alert>;
  }
  const seller = data.sellerId
    ? await member
        .GET("/v1/admin/sellers/{sellerId}", { params: { path: { sellerId: data.sellerId } } })
        .catch(() => ({ data: undefined }))
    : { data: undefined };
  const account = seller.data;
  const period =
    data.periodStart && data.periodEnd ? periodLabel(data.periodStart, data.periodEnd) : "";
  return (
    <>
      <PageHeader
        title={`${account?.storeName ?? "판매자"} ${period} 정산`}
        description={`구매확정 ${(data.itemCount ?? 0).toLocaleString("ko-KR")}건 · ${formatDateTime(data.confirmedAt)} 확정`}
        actions={
          <Button asChild variant="outline">
            <Link href="/settlements">정산 목록</Link>
          </Button>
        }
      />
      {query.notice === "paid-out" ? (
        <Alert variant="success" className="mb-4">
          지급 완료로 기록했습니다. 판매자에게 알림이 갑니다.
        </Alert>
      ) : null}
      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>정산 금액</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm">
            <p className="flex flex-wrap items-center gap-2">
              {data.status ? (
                <Badge variant={data.status === "PAID_OUT" ? "success" : "secondary"}>
                  {settlementStatusLabel(data.status)}
                </Badge>
              ) : null}
              {data.paidOutAt ? <span>{formatDateTime(data.paidOutAt)} 지급</span> : null}
            </p>
            <SettlementAmounts amounts={data} total={data.amount} totalLabel="정산 금액" />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>지급</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm">
            {account ? (
              <dl className="grid grid-cols-[6rem_1fr] gap-y-2">
                <dt className="text-muted-foreground">상호</dt>
                <dd>{account.storeName}</dd>
                <dt className="text-muted-foreground">정산 계좌</dt>
                <dd>
                  {account.bankName} {account.accountNumberMasked}
                </dd>
                <dt className="text-muted-foreground">예금주</dt>
                <dd>{account.accountHolder}</dd>
                <dt className="text-muted-foreground">판매자 상태</dt>
                <dd>{account.status === "SUSPENDED" ? "정지" : "활동 중"}</dd>
              </dl>
            ) : (
              <Alert variant="destructive">판매자 정보를 불러오지 못했습니다.</Alert>
            )}
            {data.status === "CONFIRMED" && data.settlementId ? (
              <ConfirmActionForm
                action={payOut}
                hidden={{ settlementId: data.settlementId }}
                title={`${formatPrice(data.amount)}을 지급 완료로 기록할까요?`}
                description="정산 계좌로 송금한 뒤 기록합니다. 되돌릴 수 없고, 판매자에게 지급 알림이 갑니다. 감사 로그에 남습니다."
                confirmText="지급 완료"
              >
                지급 완료로 기록
              </ConfirmActionForm>
            ) : null}
          </CardContent>
        </Card>
      </div>
      <div className="mt-6">
        {!items.data ? (
          <Alert variant="destructive">정산 내역을 불러오지 못했습니다.</Alert>
        ) : (
          <SettlementItems
            items={items.data.items ?? []}
            basePath={`/settlements/${settlementId}`}
            cursor={cursor}
            nextCursor={items.data.nextCursor}
          />
        )}
      </div>
    </>
  );
}
