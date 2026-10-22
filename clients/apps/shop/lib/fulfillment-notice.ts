/** 배송 조회 화면의 결과 문구. 작업이 끝나면 `?notice=`로 돌아와 위에 보여 준다. */
export const FULFILLMENT_NOTICES = {
  cancelled: "주문을 취소했습니다. 결제 금액은 곧 환불됩니다.",
  confirmed: "구매를 확정했습니다.",
  "claim-requested": "신청했습니다. 판매자가 회수한 상품을 확인한 뒤 처리합니다.",
  "mediation-requested": "중재를 요청했습니다. 관리자가 판단한 결과를 알려 드립니다.",
} as const;

export type FulfillmentNotice = keyof typeof FULFILLMENT_NOTICES;

/** 하위 주문 배송 조회 화면. */
export function sellerOrderPath(
  orderId: string,
  sellerOrderId: string,
  notice?: FulfillmentNotice,
): string {
  const path = `/orders/${orderId}/seller-orders/${sellerOrderId}`;
  return notice ? `${path}?notice=${notice}` : path;
}

export function fulfillmentNotice(value: string | string[] | undefined): string | null {
  return typeof value === "string" && value in FULFILLMENT_NOTICES
    ? FULFILLMENT_NOTICES[value as FulfillmentNotice]
    : null;
}
