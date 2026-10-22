import {
  Alert,
  Card,
  CardContent,
  ClaimCard,
  formatDateTime,
  formatPrice,
  listQuery,
  PAGE_SIZE,
  PageHeader,
  Pager,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { MediationDecision } from "./mediation-decision";

/**
 * 클레임 중재(CLM-3, ADM-2). 판매자가 거부한 반품·교환에 구매자가 중재를 요청한 것을 요청이 오래된 순으로 보여 준다. 사진은 관리자 경로로
 * 받는다. 결정하면 목록에서 빠진다.
 */
export default async function ClaimsPage({ searchParams }: PageProps<"/claims">) {
  await requireSession("/claims");
  const params = await searchParams;
  const { page } = listQuery(params, [], undefined);
  const notice = noticeMessage(params.notice);
  const returnTo = `/claims?page=${page}`;
  const fulfillment = await bff.serviceClient("fulfillment");
  const { data, response } = await fulfillment.GET("/v1/admin/claims", {
    params: { query: { page, size: PAGE_SIZE } },
  });
  const items = data?.items ?? [];
  return (
    <>
      <PageHeader
        title="클레임 중재"
        description="판매자가 거부한 반품·교환을 구매자가 다시 판단해 달라고 요청한 것입니다. 요청이 오래된 순입니다."
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>중재를 기다리는 클레임이 없습니다.</Alert>
      ) : (
        <div className="grid gap-6">
          {items.map((item) => (
            <section key={item.claim?.claimId} className="grid gap-2">
              <Card>
                <CardContent className="grid gap-1 pt-6 text-sm sm:grid-cols-2">
                  <p>
                    <span className="text-muted-foreground">중재 요청</span>{" "}
                    {formatDateTime(item.mediationRequestedAt)}
                  </p>
                  <p>
                    <span className="text-muted-foreground">하위 주문 금액</span>{" "}
                    {formatPrice(item.amount)}
                  </p>
                  <p className="break-all">
                    <span className="text-muted-foreground">주문</span> {item.orderId}
                  </p>
                  <p className="break-all">
                    <span className="text-muted-foreground">판매자</span> {item.sellerId}
                  </p>
                </CardContent>
              </Card>
              {item.claim ? (
                <ClaimCard claim={item.claim} imageBase="/api/admin/claim-images">
                  {item.claim.type === "RETURN" || item.claim.type === "EXCHANGE" ? (
                    <MediationDecision
                      claimId={item.claim.claimId ?? ""}
                      type={item.claim.type}
                      returnTo={returnTo}
                    />
                  ) : null}
                </ClaimCard>
              ) : null}
            </section>
          ))}
        </div>
      )}
      {data ? (
        <Pager
          basePath="/claims"
          status={undefined}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
