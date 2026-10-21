import {
  Alert,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatPrice,
  PageHeader,
} from "@openmarket/ui";
import Link from "next/link";
import { randomUUID } from "node:crypto";
import { bff } from "@/lib/bff";
import { checkoutLines } from "@/lib/checkout";
import { requireSession } from "@/lib/session";
import { CheckoutForm, type CheckoutAddress } from "./checkout-form";

/**
 * 주문서(ORD-2). 장바구니에서 고른 SKU(`?skuId=`)를 지금 장바구니에서 다시 읽어, 살 수 있는 줄만 주문한다. 가격은 지금 가격이고 order가
 * 주문할 때 다시 비교한다. 배송지는 member 배송지 목록에서 고른다(기본 배송지가 처음 선택된다).
 */
export default async function CheckoutPage({ searchParams }: PageProps<"/checkout">) {
  const { skuId } = await searchParams;
  const skuIds = (Array.isArray(skuId) ? skuId : skuId ? [skuId] : []).filter(Boolean);
  const query = new URLSearchParams(skuIds.map((id) => ["skuId", id])).toString();
  await requireSession(`/checkout${query ? `?${query}` : ""}`);

  const [order, member] = await Promise.all([
    bff.serviceClient("order"),
    bff.serviceClient("member"),
  ]);
  const [{ data: cart, response }, { data: addresses = [] }] = await Promise.all([
    order.GET("/v1/cart"),
    member.GET("/v1/members/me/addresses"),
  ]);
  if (!cart) {
    return (
      <Alert variant="destructive">
        {response.status === 503
          ? "상품 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요."
          : "장바구니를 불러오지 못했습니다."}
      </Alert>
    );
  }
  const lines = checkoutLines(cart.sellers ?? [], skuIds);
  if (lines.length === 0) {
    return (
      <>
        <PageHeader title="주문서" />
        <Alert>
          주문할 수 있는 상품이 없습니다. 장바구니에서 구매 가능한 상품을 골라 주세요.{" "}
          <Link href="/cart" className="font-medium underline">
            장바구니로
          </Link>
        </Alert>
      </>
    );
  }
  const amount = lines.reduce((sum, line) => sum + line.amount, 0);
  const choices: CheckoutAddress[] = addresses.map((address) => ({
    addressId: address.addressId ?? "",
    label: address.label ?? (address.isDefault ? "기본 배송지" : "배송지"),
    recipient: `${address.recipientName} · ${address.recipientPhone}`,
    address: `(${address.zipCode}) ${address.baseAddress} ${address.detailAddress ?? ""}`.trim(),
    isDefault: address.isDefault ?? false,
  }));
  return (
    <>
      <PageHeader title="주문서" description="가격과 재고는 주문할 때 다시 확인합니다." />
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="text-base">주문 상품 {lines.length}개</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="divide-y text-sm">
              {lines.map((line) => (
                <li
                  key={line.skuId}
                  className="flex justify-between gap-4 py-3 first:pt-0 last:pb-0"
                >
                  <span className="grid gap-0.5">
                    <span className="text-muted-foreground">{line.storeName}</span>
                    <span className="font-medium">{line.productName}</span>
                    {line.options ? (
                      <span className="text-muted-foreground">{line.options}</span>
                    ) : null}
                    <span>
                      {formatPrice(line.expectedPrice)} × {line.quantity}개
                    </span>
                  </span>
                  <span className="font-semibold whitespace-nowrap">
                    {formatPrice(line.amount)}
                  </span>
                </li>
              ))}
            </ul>
            <p className="mt-4 flex justify-between border-t pt-3 text-sm">
              <span>배송비</span>
              <span>무료</span>
            </p>
            <p className="mt-1 flex justify-between text-base font-semibold">
              <span>결제 금액</span>
              <span>{formatPrice(amount)}</span>
            </p>
          </CardContent>
        </Card>
        <Card className="h-fit">
          <CardContent>
            {choices.length === 0 ? (
              <Alert>
                배송지가 없습니다.{" "}
                <Link href="/my/addresses/new" className="font-medium underline">
                  배송지 추가
                </Link>
                후 다시 주문해 주세요.
              </Alert>
            ) : (
              <CheckoutForm
                lines={lines}
                addresses={choices}
                amount={amount}
                idempotencyKey={randomUUID()}
              />
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
