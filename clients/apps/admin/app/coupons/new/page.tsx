import { CouponForm, PageHeader, toKstInput } from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { requireSession } from "@/lib/session";
import { createPlatformCoupon } from "../actions";

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

/** 발급 기간 기본값. 다음 정시부터 7일이다(판매자 쿠폰과 같다). */
function defaultPeriod(now: Date): { issueStartAt: string; issueEndAt: string } {
  const start = Math.ceil(now.getTime() / HOUR_MS) * HOUR_MS;
  return {
    issueStartAt: toKstInput(new Date(start)),
    issueEndAt: toKstInput(new Date(start + 7 * DAY_MS)),
  };
}

/** 플랫폼 쿠폰 만들기(PRM-1). */
export default async function NewCouponPage() {
  await requireSession("/coupons/new");
  return (
    <>
      <PageHeader
        title="플랫폼 쿠폰 만들기"
        description="구매자가 쿠폰 화면에서 선착순으로 받습니다. 한 주문에 플랫폼 쿠폰 한 장을 판매자 쿠폰과 함께 쓸 수 있습니다."
      />
      <CouponForm
        action={createPlatformCoupon}
        idempotencyKey={randomUUID()}
        defaults={defaultPeriod(new Date())}
        minOrderHint="주문 전체 상품 금액(판매자 쿠폰 할인 뒤)과 비교합니다."
        scopeNote="모든 판매자 상품에 쓰는 오픈 마켓 쿠폰입니다. 할인은 플랫폼이 부담해 판매자 정산에서 빼지 않습니다. 만든 뒤에는 내용을 바꿀 수 없고 발급 중지만 할 수 있습니다."
      />
    </>
  );
}
