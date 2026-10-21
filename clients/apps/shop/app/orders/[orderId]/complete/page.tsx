import {
  Alert,
  Button,
  Card,
  CardContent,
  formatDateTime,
  PageHeader,
  ShippingCard,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { AutoRefresh } from "@/components/auto-refresh";
import { OrderItemsCard } from "@/components/order-items-card";
import { bff } from "@/lib/bff";
import { cancelReasonMessage } from "@/lib/checkout";
import { requireSession } from "@/lib/session";

/**
 * 결제 완료 화면. 승인 요청이 끝나면 여기로 온다. 주문은 payment의 승인 이벤트를 받아 결제 완료가 되므로, 그 전에는 화면을 다시 그리며
 * 기다린다(승인 결과를 바로 알지 못한 결제도 같다). 승인 직전에 주문이 취소됐으면 승인된 금액이 자동으로 취소된다고 알린다.
 */
export default async function CompletePage({ params }: PageProps<"/orders/[orderId]/complete">) {
  const { orderId } = await params;
  await requireSession(`/orders/${orderId}/complete`);
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
  if (data.status === "PENDING") {
    redirect(`/orders/${orderId}/pay`);
  }
  if (data.status === "CANCELLED") {
    return (
      <>
        <PageHeader title="주문이 취소되었습니다" />
        <Alert variant="destructive">
          {cancelReasonMessage(data.cancelReason)} 결제가 승인되었다면 자동으로 취소됩니다.{" "}
          <Link href="/cart" className="font-medium underline">
            장바구니로
          </Link>
        </Alert>
      </>
    );
  }
  if (data.status === "AWAITING_PAYMENT") {
    return (
      <>
        <PageHeader title="결제를 확인하고 있습니다" />
        <Card>
          <CardContent className="grid gap-2">
            <p className="text-sm text-muted-foreground">
              결제 결과를 받는 대로 이 화면이 바뀝니다. 창을 닫아도 결제는 계속 처리됩니다.
            </p>
            <AutoRefresh />
          </CardContent>
        </Card>
      </>
    );
  }
  return (
    <>
      <PageHeader
        title="결제가 완료되었습니다"
        description={`결제 시각 ${formatDateTime(data.paidAt)}`}
      />
      <div className="grid gap-4">
        <OrderItemsCard sellerOrders={data.sellerOrders} amount={data.amount} />
        <ShippingCard shipping={data.shipping} />
        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href={`/orders/${orderId}`}>주문 상세 보기</Link>
          </Button>
          <Button asChild variant="outline">
            <Link href="/products">쇼핑 계속하기</Link>
          </Button>
        </div>
      </div>
    </>
  );
}
