import type { ComponentProps } from "react";
import { Badge } from "./badge";

/** 하위 주문 진행 상태 이름(docs/architecture/order.md "하위 주문 진행 상태"). 구매자·판매자 화면이 같은 이름을 쓴다. */
export const PROGRESS_LABELS = {
  PAID: "결제 완료",
  ACCEPTED: "상품 준비 중",
  SHIPPED: "발송",
  IN_TRANSIT: "배송 중",
  DELIVERED: "배송 완료",
  CONFIRMED: "구매확정",
  CANCELLED: "취소 완료",
  RETURNED: "반품 완료",
} as const;

/** 배송 상태 이름(docs/glossary.md "배송"). */
export const SHIPMENT_STATUS_LABELS = {
  READY: "결제 완료",
  ACCEPTED: "상품 준비 중",
  SHIPPED: "발송",
  IN_TRANSIT: "배송 중",
  DELIVERED: "배송 완료",
  CANCELLED: "취소됨",
} as const;

export const CLAIM_TYPE_LABELS = { CANCEL: "취소", RETURN: "반품", EXCHANGE: "교환" } as const;

export const CLAIM_STATUS_LABELS = {
  REQUESTED: "신청",
  APPROVED: "승인",
  REJECTED: "거부",
  MEDIATING: "중재 중",
  COMPLETED: "완료",
} as const;

/** 클레임 사유(docs/architecture/fulfillment.md "사유와 귀책"). */
export const CLAIM_REASON_LABELS = {
  CHANGE_OF_MIND: "단순 변심",
  DEFECTIVE: "상품 불량",
  WRONG_ITEM: "오배송",
  NOT_AS_DESCRIBED: "상품 정보와 다름",
  BUYER_CANCEL: "발송 전 취소",
} as const;

export const FAULT_LABELS = { BUYER: "구매자", SELLER: "판매자" } as const;

/** 택배사(docs/architecture/fulfillment.md "발주 확인과 발송"). 연동하지 않으므로 이름과 코드만 둔다. */
export const CARRIER_LABELS = {
  CJ_LOGISTICS: "CJ대한통운",
  HANJIN: "한진택배",
  LOTTE: "롯데택배",
  LOGEN: "로젠택배",
  KOREA_POST: "우체국택배",
} as const;

function label(labels: Record<string, string>, value: string | undefined): string {
  return value && value in labels ? (labels[value] as string) : "-";
}

export const progressLabel = (progress: string | undefined) => label(PROGRESS_LABELS, progress);
export const shipmentStatusLabel = (status: string | undefined) =>
  label(SHIPMENT_STATUS_LABELS, status);
export const claimReasonLabel = (reason: string | undefined) => label(CLAIM_REASON_LABELS, reason);
export const faultLabel = (fault: string | undefined) => label(FAULT_LABELS, fault);
export const carrierLabel = (carrier: string | undefined) => label(CARRIER_LABELS, carrier);

type ClaimSummary = { type?: string; status?: string };

/** "반품 신청"처럼 유형과 상태를 잇는다. 발송 전 취소가 끝나면 "취소 완료"다. */
export function claimLabel(claim: ClaimSummary | undefined): string {
  if (!claim?.type || !claim.status) {
    return "";
  }
  return `${label(CLAIM_TYPE_LABELS, claim.type)} ${label(CLAIM_STATUS_LABELS, claim.status)}`;
}

type Variant = ComponentProps<typeof Badge>["variant"];

const PROGRESS_VARIANTS: Record<string, Variant> = {
  PAID: "default",
  ACCEPTED: "secondary",
  CONFIRMED: "success",
  CANCELLED: "destructive",
  RETURNED: "destructive",
};

/** 하위 주문 진행 상태 표시. 결제 전이라 진행 상태가 없으면 "-"다. */
export function ProgressBadge({ progress }: { progress: string | undefined }) {
  return (
    <Badge variant={(progress && PROGRESS_VARIANTS[progress]) || "outline"}>
      {progressLabel(progress)}
    </Badge>
  );
}

const CLAIM_VARIANTS: Record<string, Variant> = {
  REQUESTED: "default",
  MEDIATING: "default",
  REJECTED: "destructive",
  COMPLETED: "secondary",
};

/** 하위 주문 클레임 표시. 클레임이 없으면 그리지 않는다. */
export function ClaimBadge({ claim }: { claim: ClaimSummary | undefined }) {
  const text = claimLabel(claim);
  if (!text) {
    return null;
  }
  return (
    <Badge variant={(claim?.status && CLAIM_VARIANTS[claim.status]) || "outline"}>{text}</Badge>
  );
}
