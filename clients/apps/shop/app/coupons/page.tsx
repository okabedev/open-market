import { Alert, PageHeader } from "@openmarket/ui";
import { CouponList } from "@/components/coupon-list";
import { bff } from "@/lib/bff";

/**
 * 쿠폰 받기(PRM-2). 지금 받을 수 있는 플랫폼 쿠폰(발급 기간이 먼저 끝나는 순)을 보인다. 판매자 쿠폰은 그 판매자의 상품 상세에서 받는다.
 * 목록은 공개이고, 받기는 로그인해야 한다.
 */
export default async function CouponsPage({ searchParams }: PageProps<"/coupons">) {
  const { notice } = await searchParams;
  const promotion = await bff.publicClient("promotion");
  const result = await promotion.GET("/v1/coupons").catch(() => null);
  const coupons = result?.response.ok ? (result.data?.items ?? []) : null;
  return (
    <>
      <PageHeader
        title="쿠폰 받기"
        description="받은 쿠폰은 주문서에서 쓸 수 있습니다. 판매자 쿠폰은 상품 상세에서 받습니다."
      />
      <section id="coupons">
        {coupons ? (
          <CouponList
            coupons={coupons}
            returnTo="/coupons"
            notice={notice}
            empty="지금 받을 수 있는 쿠폰이 없습니다."
          />
        ) : (
          <Alert variant="destructive">쿠폰을 불러오지 못했습니다.</Alert>
        )}
      </section>
    </>
  );
}
