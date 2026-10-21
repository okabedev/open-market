import {
  Alert,
  Button,
  Card,
  CardContent,
  ConfirmActionForm,
  formatDateTime,
  OrderStatusBadge,
  PageHeader,
  ShippingCard,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { OrderItemsCard } from "@/components/order-items-card";
import { bff } from "@/lib/bff";
import { cancelReasonMessage } from "@/lib/checkout";
import { requireSession } from "@/lib/session";
import { cancelOrder } from "../actions";

/**
 * 본인 주문 상세(ORD-3). 결제 대기 주문은 결제 화면으로 가거나 취소할 수 있고, 재고 확인 중인 주문도 취소할 수 있다(order.md "구매자
 * 취소"). 결제된 주문의 취소·반품은 M4 클레임에서 한다.
 */
export default async function OrderPage({ params }: PageProps<"/orders/[orderId]">) {
  const { orderId } = await params;
  await requireSession(`/orders/${orderId}`);
  const order = await bff.serviceClient("order");
  const { data, response } = await order.GET("/v1/orders/{orderId}", {
    params: { path: { orderId } },
  });
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">주문을 불러오지 못했습니다.</Alert>;
  }
  const cancellable = data.status === "PENDING" || data.status === "AWAITING_PAYMENT";
  return (
    <>
      <PageHeader
        title={data.orderName ?? "주문"}
        description={`주문 시각 ${formatDateTime(data.placedAt)}`}
        actions={
          <Button asChild variant="outline">
            <Link href="/orders">주문 내역</Link>
          </Button>
        }
      />
      <div className="grid gap-4">
        <Card>
          <CardContent className="grid gap-3 pt-6 text-sm">
            <p className="flex items-center gap-2">
              <span className="text-muted-foreground">상태</span>
              <OrderStatusBadge status={data.status} />
            </p>
            {data.status === "CANCELLED" ? <p>{cancelReasonMessage(data.cancelReason)}</p> : null}
            {data.paidAt ? (
              <p>
                <span className="text-muted-foreground">결제 시각</span>{" "}
                {formatDateTime(data.paidAt)}
              </p>
            ) : null}
            {data.status === "PENDING" ? (
              <p className="text-muted-foreground">
                재고를 확인하고 있습니다. 잠시 뒤 결제할 수 있습니다.
              </p>
            ) : null}
            {cancellable ? (
              <div className="flex flex-wrap items-start gap-2">
                {data.status === "AWAITING_PAYMENT" ? (
                  <Button asChild size="sm">
                    <Link href={`/orders/${orderId}/pay`}>결제하기</Link>
                  </Button>
                ) : null}
                <ConfirmActionForm
                  action={cancelOrder}
                  hidden={{ orderId }}
                  variant="outline"
                  title="주문을 취소할까요?"
                  description="결제 전 주문만 취소할 수 있습니다. 담아 둔 재고가 풀립니다."
                  confirmText="주문 취소"
                >
                  주문 취소
                </ConfirmActionForm>
              </div>
            ) : null}
          </CardContent>
        </Card>
        <OrderItemsCard sellerOrders={data.sellerOrders} amount={data.amount} />
        <ShippingCard shipping={data.shipping} />
      </div>
    </>
  );
}
