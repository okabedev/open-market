import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatPrice,
  PageHeader,
  ProductImage,
} from "@openmarket/ui";
import Link from "next/link";
import { QuantityForm } from "@/components/quantity-form";
import { bff } from "@/lib/bff";
import { cartItemStatusLabel, cartSummary } from "@/lib/cart";
import { requireSession } from "@/lib/session";
import { changeCartItemQuantity, removeCartItem } from "./actions";

/**
 * 장바구니(ORD-1). 판매자별로 묶고, 상품 정보와 줄 상태는 볼 때마다 catalog에서 읽은 값이다(docs/architecture/order.md "장바구니").
 * 합계는 지금 살 수 있는 줄만 더한다. 주문하기는 주문서 화면과 함께 연다.
 */
export default async function CartPage() {
  await requireSession("/cart");
  const order = await bff.serviceClient("order");
  const { data: cart, response } = await order.GET("/v1/cart");
  if (!cart) {
    return (
      <>
        <PageHeader title="장바구니" />
        <Alert variant="destructive">
          {response.status === 503
            ? "상품 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요."
            : "장바구니를 불러오지 못했습니다."}
        </Alert>
      </>
    );
  }
  const sellers = cart.sellers ?? [];
  const missing = cart.missingSkuIds ?? [];
  const summary = cartSummary(sellers);
  return (
    <>
      <PageHeader
        title="장바구니"
        description="가격과 재고는 지금 기준입니다. 주문할 때 다시 확인합니다."
      />
      {missing.length > 0 ? (
        <Alert variant="destructive" className="mb-4">
          <p>
            판매 정보를 찾을 수 없는 상품이 {missing.length}개 있습니다. 장바구니에서 빼 주세요.
          </p>
          <ul className="mt-2 flex flex-wrap gap-2">
            {missing.map((skuId) => (
              <li key={skuId}>
                <form action={removeCartItem}>
                  <input type="hidden" name="skuId" value={skuId} />
                  <Button type="submit" size="sm" variant="outline">
                    빼기
                  </Button>
                </form>
              </li>
            ))}
          </ul>
        </Alert>
      ) : null}
      {sellers.length === 0 ? (
        missing.length === 0 ? (
          <Alert>
            장바구니가 비어 있습니다.{" "}
            <Link href="/products" className="font-medium underline">
              상품 보러 가기
            </Link>
          </Alert>
        ) : null
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
          <ul className="grid content-start gap-4">
            {sellers.map((seller) => (
              <li key={seller.sellerId}>
                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">{seller.storeName}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ul className="divide-y">
                      {(seller.items ?? []).map((item) => {
                        const options = (item.optionValues ?? []).join(" / ");
                        return (
                          <li
                            key={item.skuId}
                            className="flex flex-wrap items-start gap-4 py-4 first:pt-0 last:pb-0"
                          >
                            <ProductImage
                              fileId={item.thumbnailFileId}
                              size={80}
                              alt={item.productName ?? "상품"}
                            />
                            <div className="grid min-w-48 flex-1 gap-1 text-sm">
                              <Link
                                href={`/products/${item.productId}`}
                                className="font-medium hover:underline"
                              >
                                {item.productName}
                              </Link>
                              {options ? <p className="text-muted-foreground">{options}</p> : null}
                              <p>
                                {formatPrice(item.price)} × {item.quantity}개 ={" "}
                                <span className="font-semibold">
                                  {formatPrice((item.price ?? 0) * (item.quantity ?? 0))}
                                </span>
                              </p>
                              <p>
                                <Badge
                                  variant={item.status === "AVAILABLE" ? "success" : "secondary"}
                                >
                                  {cartItemStatusLabel(item.status)}
                                </Badge>
                              </p>
                            </div>
                            <div className="grid justify-items-end gap-2">
                              <QuantityForm
                                // 수량이 바뀌면 입력을 새 값으로 다시 그린다.
                                key={`${item.skuId}-${item.quantity}`}
                                action={changeCartItemQuantity}
                                hidden={{ skuId: item.skuId ?? "" }}
                                id={`quantity-${item.skuId}`}
                                label={`${item.productName} ${options} 수량`}
                                defaultQuantity={item.quantity ?? 1}
                                submitText="변경"
                              />
                              <form action={removeCartItem}>
                                <input type="hidden" name="skuId" value={item.skuId} />
                                <Button type="submit" size="sm" variant="ghost">
                                  빼기
                                </Button>
                              </form>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>
          <Card className="h-fit">
            <CardHeader>
              <CardTitle className="text-base">합계</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              <p className="flex justify-between">
                <span>구매 가능한 상품</span>
                <span>
                  {summary.orderableCount} / {summary.itemCount}개
                </span>
              </p>
              <p className="flex justify-between">
                <span>배송비</span>
                <span>무료</span>
              </p>
              <p className="flex justify-between text-base font-semibold">
                <span>결제 예정 금액</span>
                <span>{formatPrice(summary.orderableAmount)}</span>
              </p>
              {summary.orderableCount < summary.itemCount ? (
                <p className="text-xs text-muted-foreground">
                  품절·재고 부족·판매 중지 상품은 금액에 넣지 않았습니다.
                </p>
              ) : null}
            </CardContent>
          </Card>
        </div>
      )}
    </>
  );
}
