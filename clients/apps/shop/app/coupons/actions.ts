"use server";

import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { currentSession } from "@/lib/session";

/**
 * 쿠폰을 받는다(PRM-2). 쿠폰 목록(`/coupons`, 상품 상세)이 부르고, 결과는 돌아갈 화면에 `?notice=`로 붙인다. 이미 받은 쿠폰이면 promotion이
 * 같은 발급 쿠폰을 200으로 주므로 다시 눌러도 결과가 같다. 그래서 `Idempotency-Key`는 붙이지 않는다(promotion.md "선착순 발급").
 */
export async function issueCoupon(form: FormData): Promise<void> {
  const returnTo = appPath(form.get("returnTo"));
  if (!(await currentSession())) {
    redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  }
  const couponId = z.uuid().safeParse(form.get("couponId"));
  if (!couponId.success) {
    redirect(withNotice(returnTo, "coupon-failed"));
  }
  const promotion = await bff.serviceClient("promotion");
  const { error, response } = await promotion.POST("/v1/coupons/{couponId}/issue", {
    params: { path: { couponId: couponId.data } },
  });
  redirect(withNotice(returnTo, notice(response.status, error)));
}

function notice(status: number, error: unknown): string {
  if (status === 200 || status === 201) {
    return "coupon-issued";
  }
  if (status === 429) {
    return "coupon-busy";
  }
  const code = (error as { code?: string } | undefined)?.code;
  if (code === "PROMOTION_COUPON_SOLD_OUT") {
    return "coupon-sold-out";
  }
  if (code === "PROMOTION_COUPON_NOT_ISSUING" || status === 404) {
    return "coupon-not-issuing";
  }
  return "coupon-failed";
}

/** 돌아갈 화면. 폼 값이라 이 앱 안의 경로(`/`로 시작하고 `//`, `/\`가 아닌 것)만 믿는다. */
function appPath(value: FormDataEntryValue | null): string {
  return typeof value === "string" && /^\/(?![/\\])/.test(value) ? value : "/coupons";
}

function withNotice(path: string, value: string): string {
  return `${path}${path.includes("?") ? "&" : "?"}notice=${value}#coupons`;
}
