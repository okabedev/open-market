import { Badge } from "@openmarket/ui";
import type { ComponentProps } from "react";
import { statusLabel } from "@/lib/product-form";

const VARIANTS: Record<string, ComponentProps<typeof Badge>["variant"]> = {
  DRAFT: "secondary",
  ON_SALE: "success",
  STOPPED: "outline",
  BANNED: "destructive",
};

/** 상품 상태 표시. */
export function StatusBadge({ status }: { status: string | undefined }) {
  return <Badge variant={(status && VARIANTS[status]) || "outline"}>{statusLabel(status)}</Badge>;
}
