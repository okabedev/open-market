import { Alert, Card, CardContent, formatPrice, PageHeader } from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { randomUUID } from "node:crypto";
import { bff } from "@/lib/bff";
import { buyerActions } from "@/lib/claim";
import { sellerOrderPath } from "@/lib/fulfillment-notice";
import { requireSession } from "@/lib/session";
import { ClaimForm } from "./claim-form";

/**
 * 반품·교환 신청(CLM-2). 하위 주문 전체가 대상이다. 배송 완료 뒤 구매확정 전, 진행 중인 클레임이 없을 때만 폼을 보인다. 신청하면 배송
 * 조회로 돌아간다.
 */
export default async function ClaimPage({
  params,
}: PageProps<"/orders/[orderId]/seller-orders/[sellerOrderId]/claim">) {
  const { orderId, sellerOrderId } = await params;
  const back = sellerOrderPath(orderId, sellerOrderId);
  await requireSession(`${back}/claim`);
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
  const sellerOrder = data?.sellerOrders?.find(
    (candidate) => candidate.sellerOrderId === sellerOrderId,
  );
  if (data && !sellerOrder) {
    notFound();
  }
  if (!sellerOrder || !progress.data) {
    return <Alert variant="destructive">주문을 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      <PageHeader
        title="반품·교환 신청"
        description={`${sellerOrder.storeName}의 상품 전체가 대상입니다.`}
      />
      <div className="grid max-w-2xl gap-4">
        <Card>
          <CardContent className="grid gap-1 pt-6 text-sm">
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
          </CardContent>
        </Card>
        {buyerActions(progress.data).claim ? (
          <Card>
            <CardContent className="pt-6">
              <ClaimForm
                orderId={orderId}
                sellerOrderId={sellerOrderId}
                idempotencyKey={randomUUID()}
              />
            </CardContent>
          </Card>
        ) : (
          <Alert>
            지금은 신청할 수 없습니다. 배송 완료 뒤 구매확정 전에, 진행 중인 신청이 없을 때만 할 수
            있습니다.{" "}
            <Link href={back} className="font-medium underline">
              배송 조회로
            </Link>
          </Alert>
        )}
      </div>
    </>
  );
}
