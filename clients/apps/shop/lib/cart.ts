/** 장바구니 줄 상태(docs/architecture/order.md "장바구니"). */
export type CartItemStatus = "AVAILABLE" | "INSUFFICIENT_STOCK" | "SOLD_OUT" | "NOT_ON_SALE";

const STATUS_LABELS: Record<CartItemStatus, string> = {
  AVAILABLE: "구매 가능",
  INSUFFICIENT_STOCK: "재고 부족",
  SOLD_OUT: "품절",
  NOT_ON_SALE: "판매 중지",
};

/** 줄 상태의 화면 문구. 값이 없거나 모르는 값이면 판매 중지로 본다(살 수 없다). */
export function cartItemStatusLabel(status: string | undefined): string {
  return STATUS_LABELS[status as CartItemStatus] ?? STATUS_LABELS.NOT_ON_SALE;
}

type SummaryItem = { status?: string; price?: number; quantity?: number };

/**
 * 장바구니 합계. 금액은 지금 살 수 있는 줄(`AVAILABLE`)의 개당 가격 × 수량만 더한다. 장바구니는 가격을 고정하지 않으므로 볼 때마다
 * 다시 계산한다.
 */
export function cartSummary(sellers: readonly { items?: readonly SummaryItem[] }[]) {
  const items = sellers.flatMap((seller) => seller.items ?? []);
  const orderable = items.filter((item) => item.status === "AVAILABLE");
  return {
    itemCount: items.length,
    orderableCount: orderable.length,
    orderableAmount: orderable.reduce(
      (sum, item) => sum + (item.price ?? 0) * (item.quantity ?? 0),
      0,
    ),
  };
}
