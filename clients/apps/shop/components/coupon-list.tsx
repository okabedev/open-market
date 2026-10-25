import type { PromotionComponents } from "@openmarket/api-client";
import { Alert, Badge, Button, formatDateTime } from "@openmarket/ui";
import Link from "next/link";
import { issueCoupon } from "@/app/coupons/actions";
import { bff } from "@/lib/bff";
import { couponTerms, issueNotice } from "@/lib/coupons";
import { currentSession } from "@/lib/session";

type Coupon = PromotionComponents["schemas"]["CouponView"];

/**
 * 내려받을 수 있는 쿠폰 목록과 "받기"(PRM-2). 쿠폰 받기 화면(플랫폼 쿠폰)과 상품 상세(그 판매자 쿠폰)가 쓴다. 로그인했으면 이미 받은 쿠폰은
 * "받음"으로 보인다. 받기 결과는 `?notice=`로 이 목록 위에 보인다. 쿠폰이 없으면 `empty`를 보인다(없으면 아무것도 그리지 않는다).
 */
export async function CouponList({
  coupons,
  returnTo,
  notice,
  empty,
}: {
  coupons: Coupon[];
  /** 받은 뒤 돌아올 이 화면의 경로 */
  returnTo: string;
  notice: string | string[] | undefined;
  empty?: string;
}) {
  const session = await currentSession();
  const received = session ? await receivedCouponIds() : new Set<string>();
  const message = issueNotice(notice);
  if (coupons.length === 0 && !message) {
    return empty ? <p className="text-sm text-muted-foreground">{empty}</p> : null;
  }
  return (
    <div className="grid gap-3">
      {message ? <Alert variant={message.variant}>{message.text}</Alert> : null}
      <ul className="grid gap-3 sm:grid-cols-2">
        {coupons.map((coupon) => {
          const left = Math.max(0, (coupon.quantity ?? 0) - (coupon.issuedCount ?? 0));
          const mine = received.has(coupon.couponId ?? "");
          return (
            <li
              key={coupon.couponId}
              className="flex items-center justify-between gap-3 rounded-lg border p-4"
            >
              <div className="grid gap-1 text-sm">
                <span className="font-semibold">{coupon.name}</span>
                <span>{couponTerms(coupon)}</span>
                <span className="text-xs text-muted-foreground">
                  {formatDateTime(coupon.issueEndAt)}까지 받기 · 받은 뒤 {coupon.validDays}일 동안
                  사용 · 남은 수량 {left.toLocaleString("ko-KR")}장
                </span>
              </div>
              {mine ? (
                <Badge variant="secondary">받음</Badge>
              ) : !session ? (
                <Button asChild variant="outline" size="sm">
                  <Link href={`/auth/login?returnTo=${encodeURIComponent(returnTo)}`}>
                    로그인하고 받기
                  </Link>
                </Button>
              ) : left === 0 ? (
                <Badge variant="outline">소진</Badge>
              ) : (
                <form action={issueCoupon}>
                  <input type="hidden" name="couponId" value={coupon.couponId} />
                  <input type="hidden" name="returnTo" value={returnTo} />
                  <Button type="submit" size="sm" aria-label={`${coupon.name} 받기`}>
                    받기
                  </Button>
                </form>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** 내가 받은 쿠폰 정책 ID(상태 상관없이). 쿠폰은 정책마다 한 장이다. 불러오지 못하면 비어 있다(받기를 누르면 이미 받았다고 알린다). */
async function receivedCouponIds(): Promise<Set<string>> {
  const promotion = await bff.serviceClient("promotion");
  const { data } = await promotion
    .GET("/v1/issued-coupons", { params: { query: { size: 100 } } })
    .catch(() => ({ data: undefined }));
  return new Set((data?.items ?? []).map((item) => item.couponId ?? ""));
}
