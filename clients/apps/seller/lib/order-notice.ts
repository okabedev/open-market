/** 하위 주문 처리 결과 문구. 하위 주문 상세가 `?notice=`로 받아 위에 보여 준다. */
export const ORDER_NOTICES = {
  accepted: { message: "발주를 확인했습니다. 상품을 준비해 발송해 주세요.", error: false },
  shipped: {
    message: "발송했습니다. 배송 상태는 택배 흐름에 따라 바뀝니다.",
    error: false,
  },
  "claim-approved": { message: "클레임을 승인했습니다.", error: false },
  "claim-rejected": {
    message: "클레임을 거부했습니다. 구매자는 관리자 중재를 요청할 수 있습니다.",
    error: false,
  },
  conflict: {
    message: "그사이 주문 상태가 바뀌어 처리하지 못했습니다. 지금 상태를 확인해 주세요.",
    error: true,
  },
} as const;

export type OrderNotice = keyof typeof ORDER_NOTICES;

export function orderPath(sellerOrderId: string, notice: OrderNotice): string {
  return `/orders/${sellerOrderId}?notice=${notice}`;
}

export function orderNotice(
  value: string | string[] | undefined,
): (typeof ORDER_NOTICES)[OrderNotice] | null {
  return typeof value === "string" && value in ORDER_NOTICES
    ? ORDER_NOTICES[value as OrderNotice]
    : null;
}
