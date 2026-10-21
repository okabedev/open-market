import { createHash } from "node:crypto";

type CartItem = {
  skuId?: string;
  productName?: string;
  optionValues?: string[];
  price?: number;
  quantity?: number;
  status?: string;
};

/** 주문서의 한 줄. 기대 가격은 장바구니가 보여 준 지금 가격이다. order가 주문할 때 다시 비교한다(ORD-2). */
export type CheckoutLine = {
  skuId: string;
  storeName: string;
  productName: string;
  options: string;
  expectedPrice: number;
  quantity: number;
  amount: number;
};

/** 장바구니에서 고른 SKU 중 지금 살 수 있는 줄(`AVAILABLE`)만 장바구니 순서대로 고른다. */
export function checkoutLines(
  sellers: readonly { storeName?: string; items?: readonly CartItem[] }[],
  skuIds: readonly string[],
): CheckoutLine[] {
  const selected = new Set(skuIds);
  return sellers.flatMap((seller) =>
    (seller.items ?? [])
      .filter((item) => item.status === "AVAILABLE" && selected.has(item.skuId ?? ""))
      .map((item) => {
        const price = item.price ?? 0;
        const quantity = item.quantity ?? 0;
        return {
          skuId: item.skuId ?? "",
          storeName: seller.storeName ?? "",
          productName: item.productName ?? "",
          options: (item.optionValues ?? []).join(" / "),
          expectedPrice: price,
          quantity,
          amount: price * quantity,
        };
      }),
  );
}

/**
 * 결제 승인의 `Idempotency-Key`. 토스가 돌려준 결제 키로 만들어, 승인 주소를 새로 고치거나 다시 열어도 한 번만 승인한다. 결제 키는
 * 200자까지라 해시로 줄인다(키는 100자까지, docs/architecture/data.md).
 */
export function confirmIdempotencyKey(paymentKey: string): string {
  return `confirm-${createHash("sha256").update(paymentKey).digest("hex")}`;
}

const MESSAGE_MAX_LENGTH = 200;

/** 우리가 정한 결제 오류 코드의 문구. 토스 코드는 토스가 준 문구를 쓴다. */
const MESSAGES: Record<string, string> = {
  PAY_PROCESS_CANCELED: "결제를 취소했습니다. 다시 결제할 수 있습니다.",
  PAYMENT_CANCELLED: "주문이 취소되어 결제할 수 없습니다. 승인된 금액은 자동으로 취소됩니다.",
  PAYMENT_NOT_FOUND: "결제 정보를 찾지 못했습니다. 다시 결제해 주세요.",
  PAYMENT_AMOUNT_MISMATCH: "결제 금액이 주문 금액과 다릅니다. 다시 결제해 주세요.",
  PAYMENT_NOT_CONFIRMABLE: "이미 처리 중인 결제가 있습니다. 주문 상태를 확인해 주세요.",
  INVALID_REQUEST: "결제 정보가 올바르지 않습니다. 다시 결제해 주세요.",
  UNAVAILABLE: "일시적인 오류로 결제를 확인하지 못했습니다. 잠시 뒤 주문 상태를 확인해 주세요.",
};

/**
 * 결제 화면에 보여 줄 오류 문구. 토스 실패 주소(`failUrl`)와 승인 결과가 주소로 넘긴 코드·문구에서 만든다. 주소로 들어온 값이라 길이를
 * 자른다.
 */
export function paymentErrorMessage(code: string, message: string | undefined): string {
  const text =
    MESSAGES[code] ?? (message?.trim() || "결제를 완료하지 못했습니다. 다시 시도해 주세요.");
  return text.slice(0, MESSAGE_MAX_LENGTH);
}

/** 주문 취소 사유(docs/architecture/order.md "취소 사유")의 구매자 문구. */
export function cancelReasonMessage(reason: string | undefined): string {
  switch (reason) {
    case "STOCK_UNAVAILABLE":
      return "재고가 모자라 주문이 취소되었습니다.";
    case "PAYMENT_TIMEOUT":
      return "결제 기한이 지나 주문이 취소되었습니다.";
    case "BUYER_CANCELLED":
      return "결제 전에 취소한 주문입니다.";
    default:
      return "취소된 주문입니다.";
  }
}

export type PayStage = "STOCK_CHECK" | "PREPARING" | "WIDGET" | "ERROR" | "CANCELLED";

/**
 * 결제 화면이 보여 줄 단계. 주문이 결제 대기가 되어도 payment는 그 이벤트를 받아 결제를 만들므로, 결제 조회가 404인 동안은 결제를
 * 준비하는 중으로 보고 기다린다. 그 사이 승인하면 결제가 없어 거절된다(payment.md "조회").
 *
 * @param paymentStatus 결제 조회(`GET /v1/payments/orders/{orderId}`)의 HTTP 상태. 결제 대기일 때만 부른다
 */
export function payStage(orderStatus: string | undefined, paymentStatus: number | null): PayStage {
  if (orderStatus === "PENDING") {
    return "STOCK_CHECK";
  }
  if (orderStatus === "CANCELLED") {
    return "CANCELLED";
  }
  if (paymentStatus === 404) {
    return "PREPARING";
  }
  return paymentStatus !== null && paymentStatus >= 200 && paymentStatus < 300 ? "WIDGET" : "ERROR";
}
