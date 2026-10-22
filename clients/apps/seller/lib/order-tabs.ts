import type { OrderPaths } from "@openmarket/api-client";

type ListQuery = NonNullable<OrderPaths["/v1/seller/orders"]["get"]["parameters"]["query"]>;
type Filter = Pick<ListQuery, "progress" | "claimStatus">;

/**
 * 하위 주문 목록의 할 일 탭. 진행 상태와 클레임 상태 조건으로 order 목록을 거른다(docs/architecture/order.md "조회"). 교환 재발송은
 * 진행 상태가 배송 완료로 남으므로 클레임 탭에서 찾는다.
 */
export const ORDER_TABS = {
  "to-accept": { label: "발주 확인 대기", filter: { progress: ["PAID"] } },
  "to-ship": { label: "발송 대기", filter: { progress: ["ACCEPTED"] } },
  "in-delivery": { label: "배송 중", filter: { progress: ["SHIPPED", "IN_TRANSIT"] } },
  claims: {
    label: "클레임 진행 중",
    filter: { claimStatus: ["REQUESTED", "APPROVED", "MEDIATING"] },
  },
} as const satisfies Record<string, { label: string; filter: Filter }>;

export type OrderTab = keyof typeof ORDER_TABS;

export const ORDER_TAB_KEYS = Object.keys(ORDER_TABS) as OrderTab[];

/** 탭의 목록 조건. 탭이 없으면(전체) 조건이 없다. */
export function orderTabFilter(tab: OrderTab | undefined): Filter {
  if (!tab) {
    return {};
  }
  const { filter } = ORDER_TABS[tab];
  return {
    ...("progress" in filter ? { progress: [...filter.progress] } : {}),
    ...("claimStatus" in filter ? { claimStatus: [...filter.claimStatus] } : {}),
  };
}
