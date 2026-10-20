/** 상품 작업 결과 문구. 상품 상세가 `?notice=`로 받아 위에 보여 준다. */
export const NOTICES = {
  registered: "상품을 등록했습니다. 이미지와 옵션을 확인하고 판매를 시작해 주세요.",
  updated: "상품을 고쳤습니다. 판매 중이면 검색에 몇 초 안에 반영됩니다.",
  published: "판매를 시작했습니다. 검색에 몇 초 안에 나옵니다.",
  stopped: "판매를 멈췄습니다. 검색에서 몇 초 안에 빠집니다.",
  resumed: "판매를 다시 시작했습니다.",
  "stock-adjusted": "재고를 조정했습니다.",
} as const;

export type Notice = keyof typeof NOTICES;

export function productPath(productId: string, notice: Notice): string {
  return `/products/${productId}?notice=${notice}`;
}

export function noticeMessage(value: string | string[] | undefined): string | null {
  return typeof value === "string" && value in NOTICES ? NOTICES[value as Notice] : null;
}
