import type { ComponentProps } from "react";
import { Badge } from "./badge";

/** 주문·하위 주문 상태 이름(docs/glossary.md "주문"). 구매자·판매자 화면이 같은 이름을 쓴다. */
export const ORDER_STATUS_LABELS = {
  PENDING: "재고 확인 중",
  AWAITING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  CANCELLED: "취소됨",
} as const;

export type OrderStatus = keyof typeof ORDER_STATUS_LABELS;

export function orderStatusLabel(status: string | undefined): string {
  return status && status in ORDER_STATUS_LABELS ? ORDER_STATUS_LABELS[status as OrderStatus] : "-";
}

const VARIANTS: Record<string, ComponentProps<typeof Badge>["variant"]> = {
  PENDING: "secondary",
  AWAITING_PAYMENT: "outline",
  PAID: "success",
  CANCELLED: "destructive",
};

/** 주문 상태 표시. */
export function OrderStatusBadge({ status }: { status: string | undefined }) {
  return (
    <Badge variant={(status && VARIANTS[status]) || "outline"}>{orderStatusLabel(status)}</Badge>
  );
}
