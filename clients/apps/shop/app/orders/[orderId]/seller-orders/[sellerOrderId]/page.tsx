import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ClaimBadge,
  ClaimCard,
  ConfirmActionForm,
  formatDateTime,
  formatPrice,
  PageHeader,
  ProgressBadge,
  ShipmentTable,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";
import { buyerActions, canRequestMediation } from "@/lib/claim";
import { fulfillmentNotice } from "@/lib/fulfillment-notice";
import { requireSession } from "@/lib/session";
import { cancelSellerOrder, confirmPurchase, requestMediation } from "../actions";

/**
 * 하위 주문(판매자 하나의 상품) 배송 조회와 구매자 처리(FUL-2, FUL-3, CLM-1~3). 상품과 진행 상태는 order, 배송·구매확정·클레임은
 * fulfillment에서 읽는다. 할 수 있는 일(발송 전 취소, 구매확정, 반품·교환 신청, 중재 요청)만 버튼으로 보이고 최종 판단은 fulfillment가
 * 한다.
 */
export default async function SellerOrderPage({
  params,
  searchParams,
}: PageProps<"/orders/[orderId]/seller-orders/[sellerOrderId]">) {
  const { orderId, sellerOrderId } = await params;
  const path = `/orders/${orderId}/seller-orders/${sellerOrderId}`;
  await requireSession(path);
  const notice = fulfillmentNotice((await searchParams).notice);
  const [order, fulfillment] = await Promise.all([
    bff.serviceClient("order"),
    bff.serviceClient("fulfillment"),
  ]);
  const [{ data, response }, progress] = await Promise.all([
    order.GET("/v1/orders/{orderId}", { params: { path: { orderId } } }),
    fulfillment.GET("/v1/seller-orders/{sellerOrderId}", {
      params: { path: { sellerOrderId } },
    }),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">주문을 불러오지 못했습니다.</Alert>;
  }
  const sellerOrder = data.sellerOrders?.find(
    (candidate) => candidate.sellerOrderId === sellerOrderId,
  );
  if (!sellerOrder) {
    notFound();
  }
  const view = progress.data;
  const actions = view ? buyerActions(view) : null;
  const hidden = { orderId, sellerOrderId };
  return (
    <>
      <PageHeader
        title={`${sellerOrder.storeName} 배송 조회`}
        description={`주문 ${data.orderName ?? ""} · 주문 시각 ${formatDateTime(data.placedAt)}`}
        actions={
          <Button asChild variant="outline">
            <Link href={`/orders/${orderId}`}>주문 상세</Link>
          </Button>
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <div className="grid gap-4">
        <Card>
          <CardContent className="grid gap-3 pt-6 text-sm">
            <p className="flex flex-wrap items-center gap-2">
              <span className="text-muted-foreground">진행 상태</span>
              <ProgressBadge progress={sellerOrder.progress} />
              <ClaimBadge claim={sellerOrder.claim} />
            </p>
            {view?.confirmedAt ? (
              <p>
                {formatDateTime(view.confirmedAt)}에{" "}
                {view.confirmMode === "AUTO" ? "자동으로 " : ""}구매확정했습니다.
              </p>
            ) : view?.confirmDueAt && actions?.confirm ? (
              <p className="text-muted-foreground">
                {formatDateTime(view.confirmDueAt)}에 자동으로 구매확정됩니다. 그 전까지 반품·교환을
                신청할 수 있습니다.
              </p>
            ) : null}
            {actions && (actions.cancel || actions.confirm || actions.claim) ? (
              <div className="flex flex-wrap items-start gap-2">
                {actions.confirm ? (
                  <ConfirmActionForm
                    action={confirmPurchase}
                    hidden={hidden}
                    title="구매를 확정할까요?"
                    description="확정한 뒤에는 반품·교환을 신청할 수 없습니다."
                    confirmText="구매확정"
                  >
                    구매확정
                  </ConfirmActionForm>
                ) : null}
                {actions.claim ? (
                  <Button asChild size="sm" variant="outline">
                    <Link href={`${path}/claim`}>반품·교환 신청</Link>
                  </Button>
                ) : null}
                {actions.cancel ? (
                  <ConfirmActionForm
                    action={cancelSellerOrder}
                    hidden={hidden}
                    variant="outline"
                    title="이 판매자의 상품을 취소할까요?"
                    description={`발송 전이라 바로 취소되고 ${formatPrice(sellerOrder.amount)}이 환불됩니다. 다른 판매자의 상품은 그대로입니다.`}
                    confirmText="주문 취소"
                  >
                    주문 취소
                  </ConfirmActionForm>
                ) : null}
              </div>
            ) : null}
          </CardContent>
        </Card>
        {view ? (
          <ShipmentTable shipments={view.shipments ?? []} />
        ) : progress.response.status === 404 ? (
          <Alert>배송 정보를 준비하고 있습니다. 잠시 뒤 새로 고쳐 주세요.</Alert>
        ) : (
          <Alert variant="destructive">배송 정보를 불러오지 못했습니다.</Alert>
        )}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">주문 상품</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-1 text-sm">
            {(sellerOrder.lines ?? []).map((line) => (
              <p key={line.orderLineId} className="flex justify-between gap-4">
                <span>
                  {line.productName}
                  {(line.optionValues ?? []).length > 0
                    ? ` (${(line.optionValues ?? []).join(" / ")})`
                    : ""}{" "}
                  × {line.quantity}
                </span>
                <span>{formatPrice(line.amount)}</span>
              </p>
            ))}
            <p className="flex justify-between border-t pt-2 font-semibold">
              <span>금액</span>
              <span>{formatPrice(sellerOrder.amount)}</span>
            </p>
          </CardContent>
        </Card>
        {[...(view?.claims ?? [])].reverse().map((claim) => (
          <ClaimCard key={claim.claimId} claim={claim}>
            {view && canRequestMediation(view, claim) ? (
              <div className="grid gap-2 rounded-md border p-4">
                <p>
                  판매자의 거부에 동의하지 않으면 관리자에게 중재를 요청할 수 있습니다. 거부된 뒤
                  7일 안에 한 번만 요청할 수 있습니다.
                </p>
                <ConfirmActionForm
                  action={requestMediation}
                  hidden={{ ...hidden, claimId: claim.claimId ?? "" }}
                  variant="outline"
                  title="관리자 중재를 요청할까요?"
                  description="관리자가 사진과 사유를 보고 승인하거나 거부를 확정합니다. 한 번만 요청할 수 있습니다."
                  confirmText="중재 요청"
                >
                  중재 요청
                </ConfirmActionForm>
              </div>
            ) : null}
          </ClaimCard>
        ))}
      </div>
    </>
  );
}
