import type { ComponentProps } from "react";
import { Badge } from "./badge";

/** 상품 상태 이름(docs/glossary.md "상품"). 판매자·관리자·구매자 화면이 같은 이름을 쓴다. */
export const PRODUCT_STATUS_LABELS = {
  DRAFT: "등록 중",
  ON_SALE: "판매 중",
  STOPPED: "판매 중지",
  BANNED: "판매 제한",
} as const;

export type ProductStatus = keyof typeof PRODUCT_STATUS_LABELS;

export const PRODUCT_STATUSES = Object.keys(PRODUCT_STATUS_LABELS) as ProductStatus[];

export function productStatusLabel(status: string | undefined): string {
  return status && status in PRODUCT_STATUS_LABELS
    ? PRODUCT_STATUS_LABELS[status as ProductStatus]
    : "-";
}

const VARIANTS: Record<string, ComponentProps<typeof Badge>["variant"]> = {
  DRAFT: "secondary",
  ON_SALE: "success",
  STOPPED: "outline",
  BANNED: "destructive",
};

/** 상품 상태 표시. */
export function ProductStatusBadge({ status }: { status: string | undefined }) {
  return (
    <Badge variant={(status && VARIANTS[status]) || "outline"}>{productStatusLabel(status)}</Badge>
  );
}
