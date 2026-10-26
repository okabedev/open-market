import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  couponTerms,
  formatDateTime,
  PageHeader,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { stateLabel } from "@/lib/coupons";
import { requireSession } from "@/lib/session";

const PAGE_SIZE = 20;

/** 내 쿠폰(PRM-2). 받은 쿠폰을 최근에 받은 순으로 보인다. 다음 쪽은 `?cursor=`로 넘긴다. */
export default async function MyCouponsPage({ searchParams }: PageProps<"/my/coupons">) {
  const { cursor } = await searchParams;
  await requireSession("/my/coupons");
  const promotion = await bff.serviceClient("promotion");
  const { data } = await promotion
    .GET("/v1/issued-coupons", {
      params: {
        query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) },
      },
    })
    .catch(() => ({ data: undefined }));
  const items = data?.items ?? [];
  return (
    <>
      <PageHeader
        title="내 쿠폰"
        description="사용 가능한 쿠폰은 주문서에서 고릅니다. 결제 전에 주문을 취소하면 쿠폰은 다시 쓸 수 있습니다."
      />
      {!data ? (
        <Alert variant="destructive">쿠폰을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>
          받은 쿠폰이 없습니다.{" "}
          <Link href="/coupons" className="font-medium underline">
            쿠폰 받기
          </Link>
        </Alert>
      ) : (
        <Card>
          <CardContent>
            <ul className="divide-y text-sm">
              {items.map((coupon) => (
                <li
                  key={coupon.issuedCouponId}
                  className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0"
                >
                  <span className="grid gap-0.5">
                    <span className="text-xs text-muted-foreground">
                      {coupon.issuer === "PLATFORM" ? "오픈 마켓 쿠폰" : "판매자 쿠폰"}
                    </span>
                    <span className="font-medium">{coupon.name}</span>
                    <span>{couponTerms(coupon)}</span>
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(coupon.expiresAt)}까지
                    </span>
                  </span>
                  {coupon.state ? (
                    <Badge variant={coupon.state === "USABLE" ? "success" : "secondary"}>
                      {stateLabel(coupon.state)}
                    </Badge>
                  ) : null}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
      <div className="mt-4 flex justify-between">
        {typeof cursor === "string" ? (
          <Button asChild variant="outline" size="sm">
            <Link href="/my/coupons">처음으로</Link>
          </Button>
        ) : (
          <span />
        )}
        {data?.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/my/coupons?cursor=${encodeURIComponent(data.nextCursor)}`}>다음</Link>
          </Button>
        ) : null}
      </div>
    </>
  );
}
