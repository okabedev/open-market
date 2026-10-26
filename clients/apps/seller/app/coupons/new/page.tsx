import { CouponForm, PageHeader, toKstInput } from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { requireSeller } from "@/lib/session";
import { createCoupon } from "../actions";

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
      <CouponForm
        action={createCoupon}
        idempotencyKey={randomUUID()}
        defaults={defaultPeriod(new Date())}
        minOrderHint="이 판매자 상품 금액의 합과 비교합니다."
        scopeNote="내 모든 상품에 쓰는 쿠폰입니다. 만든 뒤에는 내용을 바꿀 수 없고 발급 중지만 할 수 있습니다. 쿠폰 할인은 정산 금액에서 뺍니다."
      />
    </>
  );
}
