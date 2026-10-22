/**
 * 반품·교환 신청의 상한과 고를 수 있는 사유. 클라이언트 폼도 쓰므로 zod에 의존하지 않는다(검증 스키마는 `app/orders/actions.ts`). 원본은
 * fulfillment 규칙이다(docs/architecture/fulfillment.md "반품·교환 신청", "사유와 귀책").
 */
export const CLAIM_LIMITS = {
  photos: 5,
  photoBytes: 10 * 1024 * 1024,
  description: 500,
} as const;

export const CLAIM_TYPES = ["RETURN", "EXCHANGE"] as const;
export type ClaimType = (typeof CLAIM_TYPES)[number];

export const CLAIM_REASONS = [
  "CHANGE_OF_MIND",
  "DEFECTIVE",
  "WRONG_ITEM",
  "NOT_AS_DESCRIBED",
] as const;
export type ClaimReason = (typeof CLAIM_REASONS)[number];

/** 교환은 판매자 귀책 사유만 된다. 단순 변심은 반품한 뒤 다시 주문한다. */
export function reasonsFor(type: ClaimType): readonly ClaimReason[] {
  return type === "EXCHANGE"
    ? CLAIM_REASONS.filter((reason) => reason !== "CHANGE_OF_MIND")
    : CLAIM_REASONS;
}

type Shipment = { status?: string };
type Claim = { status?: string; mediationRequestedAt?: string };
type BuyerFulfillment = { shipments?: Shipment[]; claims?: Claim[]; confirmedAt?: string };

const ONGOING = new Set(["REQUESTED", "APPROVED", "MEDIATING"]);

/**
 * 구매자가 지금 할 수 있는 일(docs/architecture/fulfillment.md "구매확정", "클레임"). 하위 주문의 마지막 배송과 진행 중인 클레임으로
 * 정한다. 최종 판단은 fulfillment가 하고, 화면은 버튼을 보일지만 정한다.
 */
export function buyerActions(view: BuyerFulfillment): {
  cancel: boolean;
  confirm: boolean;
  claim: boolean;
} {
  const last = view.shipments?.at(-1)?.status;
  const ongoing = (view.claims ?? []).some((claim) => ONGOING.has(claim.status ?? ""));
  const delivered = last === "DELIVERED" && !view.confirmedAt && !ongoing;
  return {
    cancel:
      (last === "READY" || last === "ACCEPTED") && !ongoing && (view.shipments?.length ?? 0) === 1,
    confirm: delivered,
    claim: delivered,
  };
}

/**
 * 판매자가 거부한 클레임에 중재를 요청할 수 있는지. 클레임마다 한 번이고 구매확정 전, 다른 클레임이 진행 중이 아닐 때다. 거부 뒤 7일
 * 기한은 fulfillment가 본다(409 `FULFILLMENT_MEDIATION_CLOSED`).
 */
export function canRequestMediation(view: BuyerFulfillment, claim: Claim): boolean {
  const ongoing = (view.claims ?? []).some((other) => ONGOING.has(other.status ?? ""));
  return (
    claim.status === "REJECTED" && !claim.mediationRequestedAt && !view.confirmedAt && !ongoing
  );
}
