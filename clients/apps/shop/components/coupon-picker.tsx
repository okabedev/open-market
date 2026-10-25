import { Button, Label } from "@openmarket/ui";
import Link from "next/link";
import { couponTerms, type CouponChoices } from "@/lib/coupons";

// 클레임 신청 폼의 select와 같은 모양이다.
const selectClass = "h-10 w-full rounded-md border border-input bg-card px-3 text-sm";

/**
 * 주문서의 쿠폰 고르기(PRM-3). 판매자마다 그 판매자 쿠폰 한 장, 플랫폼 쿠폰 한 장을 고른다. GET 폼이라 "쿠폰 적용"을 누르면 같은 주문서가
 * 고른 쿠폰으로 할인을 다시 계산해 그린다(JS 없이 동작). 지금 주문서 주소의 조건(`query`)은 숨은 값으로 함께 보낸다.
 */
export function CouponPicker({
  choices,
  sellers,
  selected,
  query,
}: {
  choices: CouponChoices;
  /** 주문의 판매자(상호) */
  sellers: { sellerId: string; storeName: string }[];
  selected: string[];
  query: [string, string][];
}) {
  const withCoupons = sellers.filter(
    (seller) => (choices.bySeller.get(seller.sellerId) ?? []).length > 0,
  );
  if (withCoupons.length === 0 && choices.platform.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        이 주문에 쓸 수 있는 쿠폰이 없습니다.{" "}
        <Link href="/coupons" className="font-medium underline">
          쿠폰 받기
        </Link>
      </p>
    );
  }
  const chosen = new Set(selected);
  return (
    <form action="/checkout" method="get" className="grid gap-3" aria-label="쿠폰">
      {query.map(([name, value], index) => (
        <input key={`${name}-${index}`} type="hidden" name={name} value={value} />
      ))}
      {withCoupons.map((seller) => {
        const coupons = choices.bySeller.get(seller.sellerId) ?? [];
        const id = `seller-coupon-${seller.sellerId}`;
        return (
          <div key={seller.sellerId} className="grid gap-1">
            <Label htmlFor={id}>{seller.storeName} 쿠폰</Label>
            <select
              id={id}
              name="sellerCoupon"
              className={selectClass}
              defaultValue={
                coupons.find((c) => chosen.has(c.issuedCouponId ?? ""))?.issuedCouponId ?? ""
              }
            >
              <option value="">쓰지 않음</option>
              {coupons.map((coupon) => (
                <option key={coupon.issuedCouponId} value={coupon.issuedCouponId}>
                  {coupon.name} · {couponTerms(coupon)}
                </option>
              ))}
            </select>
          </div>
        );
      })}
      {choices.platform.length > 0 ? (
        <div className="grid gap-1">
          <Label htmlFor="platform-coupon">오픈 마켓 쿠폰</Label>
          <select
            id="platform-coupon"
            name="platformCoupon"
            className={selectClass}
            defaultValue={
              choices.platform.find((c) => chosen.has(c.issuedCouponId ?? ""))?.issuedCouponId ?? ""
            }
          >
            <option value="">쓰지 않음</option>
            {choices.platform.map((coupon) => (
              <option key={coupon.issuedCouponId} value={coupon.issuedCouponId}>
                {coupon.name} · {couponTerms(coupon)}
              </option>
            ))}
          </select>
        </div>
      ) : null}
      <Button type="submit" variant="outline" size="sm" className="justify-self-start">
        쿠폰 적용
      </Button>
    </form>
  );
}
