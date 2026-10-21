import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatPrice,
  PageHeader,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { AutoRefresh } from "@/components/auto-refresh";
import { PaymentWidget } from "@/components/payment-widget";
import { bff } from "@/lib/bff";
import { cancelReasonMessage, paymentErrorMessage } from "@/lib/checkout";
import { requireSession } from "@/lib/session";

/**
 * 결제 화면(PAY-1). 주문을 만든 뒤 여기로 온다. 주문 상태를 읽어 화면을 고른다.
 * - `PENDING`: 재고를 확인하는 동안 화면을 다시 그리며 기다린다
 * - `AWAITING_PAYMENT`: 토스 결제위젯. 실패하고 돌아오면(`?code=&message=`) 문구를 보여 주고 다시 결제할 수 있다
 * - `CANCELLED`: 취소 사유. `PAID`: 완료 화면으로 보낸다
 */
export default async function PayPage({
  params,
  searchParams,
}: PageProps<"/orders/[orderId]/pay">) {
  const { orderId } = await params;
  const { code, message } = await searchParams;
  await requireSession(`/orders/${orderId}/pay`);
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
  if (data.status === "PAID") {
    redirect(`/orders/${orderId}/complete`);
  }
  const failure =
    typeof code === "string"
      ? paymentErrorMessage(code, typeof message === "string" ? message : undefined)
      : null;
  return (
    <>
      <PageHeader title="결제" description={data.orderName} />
      <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
        <div className="grid content-start gap-4">
          {data.status === "PENDING" ? (
            <Card>
              <CardContent className="grid gap-2">
                <p className="font-medium">재고를 확인하고 있습니다.</p>
                <p className="text-sm text-muted-foreground">확인이 끝나면 결제 화면이 열립니다.</p>
                <AutoRefresh />
              </CardContent>
            </Card>
          ) : data.status === "CANCELLED" ? (
            <Alert variant="destructive">
              {failure && code === "PAYMENT_CANCELLED"
                ? failure
                : cancelReasonMessage(data.cancelReason)}{" "}
              <Link href="/cart" className="font-medium underline">
                장바구니로
              </Link>
            </Alert>
          ) : (
            <>
              {failure ? <Alert variant="destructive">{failure}</Alert> : null}
              <PaymentWidget
                clientKey={process.env.TOSS_CLIENT_KEY ?? ""}
                sdkUrl={process.env.TOSS_SDK_URL}
                orderId={orderId}
                orderName={data.orderName ?? ""}
                amount={data.amount ?? 0}
              />
            </>
          )}
        </div>
        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="text-base">주문 정보</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            <p className="flex justify-between gap-4">
              <span>주문</span>
              <span className="text-right">{data.orderName}</span>
            </p>
            <p className="flex justify-between">
              <span>배송비</span>
              <span>무료</span>
            </p>
            <p className="flex justify-between text-base font-semibold">
              <span>결제 금액</span>
              <span>{formatPrice(data.amount)}</span>
            </p>
            {data.status === "AWAITING_PAYMENT" ? (
              <p className="text-xs text-muted-foreground">
                결제 기한이 지나면 주문은 자동으로 취소됩니다.
              </p>
            ) : null}
            <Button asChild variant="ghost" size="sm">
              <Link href="/cart">장바구니로</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
