import { Alert, Card, CardContent, PageHeader } from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { randomUUID } from "node:crypto";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { ReviewForm } from "./review-form";

/**
 * 리뷰 쓰기(REV-1). 구매확정한 주문 상품마다 한 번 쓴다. 상품 이름과 옵션은 주문 상세에서 읽는다. 쓸 수 있는지(구매확정, 아직 안 씀)는
 * review가 보고, 안 되면 폼에 문구가 보인다. 들어오는 곳은 내 리뷰의 "쓸 수 있는 리뷰"다.
 */
export default async function WriteReviewPage({
  params,
}: PageProps<"/orders/[orderId]/reviews/[orderLineId]">) {
  const { orderId, orderLineId } = await params;
  await requireSession(`/orders/${orderId}/reviews/${orderLineId}`);
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
  const sellerOrder = data.sellerOrders?.find((candidate) =>
    (candidate.lines ?? []).some((line) => line.orderLineId === orderLineId),
  );
  const line = sellerOrder?.lines?.find((candidate) => candidate.orderLineId === orderLineId);
  if (!sellerOrder || !line) {
    notFound();
  }
  return (
    <>
      <PageHeader
        title="리뷰 쓰기"
        description="리뷰는 한 번 쓰면 고치거나 지울 수 없습니다. 상품 상세에 작성자 없이 보입니다."
      />
      <div className="grid max-w-2xl gap-4">
        <Card>
          <CardContent className="grid gap-1 pt-6 text-sm">
            <span className="text-xs text-muted-foreground">{sellerOrder.storeName}</span>
            <Link href={`/products/${line.productId}`} className="font-medium hover:underline">
              {line.productName}
            </Link>
            {(line.optionValues ?? []).length > 0 ? (
              <span className="text-muted-foreground">{(line.optionValues ?? []).join(" / ")}</span>
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <ReviewForm orderLineId={orderLineId} idempotencyKey={randomUUID()} />
          </CardContent>
        </Card>
      </div>
    </>
  );
}
