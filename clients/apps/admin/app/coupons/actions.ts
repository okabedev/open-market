"use server";

import { couponFormValues, problemState, readCouponForm, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { reviewResult } from "@/lib/review";

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}

/**
 * 플랫폼 쿠폰을 만든다(PRM-1). 모든 판매자 상품에 쓰고 할인은 플랫폼이 부담한다. 규칙은 판매자 쿠폰과 같다(`readCouponForm`). 화면을
 * 그릴 때 만든 멱등성 키를 보내 두 번 눌러도 하나만 생긴다. promotion이 감사 로그(`COUPON_CREATED`)를 남긴다.
 */
export async function createPlatformCoupon(_: FormState, form: FormData): Promise<FormState> {
  const result = readCouponForm(form, new Date());
  if (!result.success) {
    return result.state;
  }
  const promotion = await bff.serviceClient("promotion");
  const { error, response } = await promotion.POST("/v1/admin/coupons", {
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
    body: result.data,
  });
  if (!response.ok) {
    return problemState(error, response.status, couponFormValues(form));
  }
  redirect("/coupons?notice=coupon-created");
}

/**
 * 쿠폰(플랫폼·판매자)의 발급을 멈춘다(PRM-1). 끝 상태이고 이미 받은 쿠폰은 쓸 수 있다. 이미 멈췄으면 그대로 성공이다. 보던 목록 조건으로
 * 돌아간다. promotion이 감사 로그(`COUPON_STOPPED`)를 남긴다.
 */
export async function stopCoupon(_: FormState, form: FormData): Promise<FormState> {
  const couponId = z.uuid().safeParse(text(form, "couponId"));
  if (!couponId.success) {
    return { status: "error", message: "쿠폰을 찾을 수 없습니다." };
  }
  const promotion = await bff.serviceClient("promotion");
  const { error, response } = await promotion.POST("/v1/admin/coupons/{couponId}/stop", {
    params: { path: { couponId: couponId.data } },
  });
  return reviewResult(error, response, form, "/coupons", "coupon-stopped");
}
