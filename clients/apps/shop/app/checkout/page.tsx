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
import { buyNowLine, checkoutLines, type CheckoutLine } from "@/lib/checkout";
import { requireSession } from "@/lib/session";
import { CheckoutForm, type CheckoutAddress } from "./checkout-form";

/**
 * 주문서(ORD-2). 두 가지로 연다.
 * - 장바구니에서 고른 SKU(`?skuId=`): 지금 장바구니에서 다시 읽어 살 수 있는 줄만 주문한다.
 * - 바로 구매(`?buyNow=<SKU>&productId=&quantity=`): 장바구니를 거치지 않고 상품 상세의 지금 가격으로 한 줄을 주문한다. 결제되면 같은
 *   SKU가 장바구니에 있어도 빠진다(order.md "결제 결과").
 *
 * 가격은 지금 가격이고 order가 주문할 때 다시 비교한다. 배송지는 member 배송지 목록에서 고른다(기본 배송지가 처음 선택된다).
 */
export default async function CheckoutPage({ searchParams }: PageProps<"/checkout">) {
  const { skuId, buyNow, productId, quantity } = await searchParams;
  const direct =
    typeof buyNow === "string" && typeof productId === "string"
      ? { skuId: buyNow, productId, quantity: typeof quantity === "string" ? quantity : undefined }
      : null;
  const skuIds = (Array.isArray(skuId) ? skuId : skuId ? [skuId] : []).filter(Boolean);
  const query = direct
    ? new URLSearchParams({
        buyNow: direct.skuId,
        productId: direct.productId,
        quantity: direct.quantity ?? "",
      }).toString()
    : new URLSearchParams(skuIds.map((id) => ["skuId", id])).toString();
  await requireSession(`/checkout${query ? `?${query}` : ""}`);

  const member = await bff.serviceClient("member");
  const [loaded, { data: addresses = [] }] = await Promise.all([
    direct ? directLines(direct) : cartLines(skuIds),
    member.GET("/v1/members/me/addresses"),
  ]);
  if ("error" in loaded) {
    return <Alert variant="destructive">{loaded.error}</Alert>;
  }
  const { lines } = loaded;
  if (lines.length === 0) {
    return (
      <>
        <PageHeader title="주문서" />
        {direct ? (
          <Alert>
            지금 주문할 수 없는 상품입니다. 품절이거나 판매가 중지됐는지, 수량이 1~99개인지 확인해
            주세요.{" "}
            <Link href={`/products/${direct.productId}`} className="font-medium underline">
              상품으로
            </Link>
          </Alert>
        ) : (
          <Alert>
            주문할 수 있는 상품이 없습니다. 장바구니에서 구매 가능한 상품을 골라 주세요.{" "}
            <Link href="/cart" className="font-medium underline">
              장바구니로
            </Link>
          </Alert>
        )}
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

type Loaded = { lines: CheckoutLine[] } | { error: string };

/** 장바구니에서 고른 줄. */
async function cartLines(skuIds: string[]): Promise<Loaded> {
  const order = await bff.serviceClient("order");
  const { data: cart, response } = await order.GET("/v1/cart");
  if (!cart) {
    return {
      error:
        response.status === 503
          ? "상품 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요."
          : "장바구니를 불러오지 못했습니다.",
    };
  }
  return { lines: checkoutLines(cart.sellers ?? [], skuIds) };
}

/** 바로 구매 한 줄. 상품이 없으면(404) 줄이 없는 것으로 본다. */
async function directLines(direct: {
  skuId: string;
  productId: string;
  quantity: string | undefined;
}): Promise<Loaded> {
  const catalog = await bff.publicClient("catalog");
  const { data: product, response } = await catalog.GET("/v1/products/{productId}", {
    params: { path: { productId: direct.productId } },
  });
  if (!product) {
    return response.status === 404 || response.status === 400
      ? { lines: [] }
      : { error: "상품 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요." };
  }
  const line = buyNowLine(product, direct.skuId, direct.quantity);
  return { lines: line ? [line] : [] };
}
