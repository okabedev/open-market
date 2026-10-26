"use server";

import { problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { couponFormValues, readCouponForm } from "@/lib/coupon-form";

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}

/**
 * 판매자 쿠폰을 만든다(PRM-1). 그 판매자의 모든 상품에 쓰는 선착순 쿠폰이고, 만든 뒤에는 내용을 바꾸지 않는다. 화면을 그릴 때 만든 멱등성
 * 키를 보내 두 번 눌러도 하나만 생긴다. 만들면 쿠폰 목록으로 간다.
 */
export async function createCoupon(_: FormState, form: FormData): Promise<FormState> {
  const result = readCouponForm(form, new Date());
  if (!result.success) {
    return result.state;
  }
  const promotion = await bff.serviceClient("promotion");
  const { error, response } = await promotion.POST("/v1/seller/coupons", {
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
    body: result.data,
  });
  if (!response.ok) {
    return problemState(error, response.status, couponFormValues(form));
  }
  redirect("/coupons?notice=created");
}

/**
 * 자기 쿠폰의 발급을 멈춘다(PRM-1). 끝 상태이고 이미 받은 쿠폰은 그대로 쓸 수 있다. 이미 멈췄으면 그대로 성공이다. 다른 판매자의
 * 쿠폰이면 404 문구를 보인다.
 */
export async function stopCoupon(_: FormState, form: FormData): Promise<FormState> {
  const couponId = z.uuid().safeParse(text(form, "couponId"));
  if (!couponId.success) {
    return { status: "error", message: "쿠폰을 찾을 수 없습니다." };
  }
  const promotion = await bff.serviceClient("promotion");
  const { error, response } = await promotion.POST("/v1/seller/coupons/{couponId}/stop", {
    params: { path: { couponId: couponId.data } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect("/coupons?notice=stopped");
}
