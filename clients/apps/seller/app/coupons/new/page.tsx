import { PageHeader, toKstInput } from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { requireSeller } from "@/lib/session";
import { CouponForm } from "./coupon-form";

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

/** 발급 기간 기본값. 다음 정시부터 7일이다. */
function defaultPeriod(now: Date): { issueStartAt: string; issueEndAt: string } {
  const start = Math.ceil(now.getTime() / HOUR_MS) * HOUR_MS;
  return {
    issueStartAt: toKstInput(new Date(start)),
    issueEndAt: toKstInput(new Date(start + 7 * DAY_MS)),
  };
}

/** 판매자 쿠폰 만들기(PRM-1). */
export default async function NewCouponPage() {
  await requireSeller("/coupons/new");
  return (
    <>
      <PageHeader
        title="쿠폰 만들기"
        description="구매자가 상품 상세와 쿠폰 화면에서 선착순으로 받습니다."
      />
      <CouponForm idempotencyKey={randomUUID()} defaults={defaultPeriod(new Date())} />
    </>
  );
}
