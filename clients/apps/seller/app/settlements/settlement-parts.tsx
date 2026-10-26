import { Button, Card, CardContent, formatDateTime, formatPrice } from "@openmarket/ui";
import Link from "next/link";

type Amounts = {
  salesAmount?: number;
  commissionAmount?: number;
  sellerCouponDiscount?: number;
};

/**
 * 정산 금액 계산(docs/architecture/settlement.md "정산 대상"). 판매 금액(쿠폰 전) − 수수료 − 판매자 쿠폰 부담분 = 정산 금액. 플랫폼
 * 쿠폰은 플랫폼이 부담해 빼지 않는다.
 */
export function SettlementAmounts({
  amounts,
  total,
  totalLabel,
}: {
  amounts: Amounts;
  total: number | undefined;
  totalLabel: string;
}) {
  return (
    <dl className="grid grid-cols-[10rem_1fr] gap-y-2 text-sm">
      <dt className="text-muted-foreground">판매 금액</dt>
      <dd className="text-right tabular-nums">{formatPrice(amounts.salesAmount)}</dd>
      <dt className="text-muted-foreground">수수료</dt>
      <dd className="text-right tabular-nums">-{formatPrice(amounts.commissionAmount)}</dd>
      <dt className="text-muted-foreground">판매자 쿠폰 부담</dt>
      <dd className="text-right tabular-nums">-{formatPrice(amounts.sellerCouponDiscount)}</dd>
      <dt className="border-t pt-2 font-medium">{totalLabel}</dt>
      <dd className="border-t pt-2 text-right text-base font-semibold tabular-nums">
        {formatPrice(total)}
      </dd>
    </dl>
  );
}

export type SettlementItem = Amounts & {
  sellerOrderId?: string;
  confirmedAt?: string;
  settlementAmount?: number;
  lines?: {
    orderLineId?: string;
    productName?: string;
    quantity?: number;
    amount?: number;
    commissionRateBp?: number;
    commissionAmount?: number;
  }[];
};

/** 요율(bp)을 %로. 예: 1050 → 10.5% */
function rate(bp: number | undefined): string {
  return `${((bp ?? 0) / 100).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}%`;
}

/**
 * 정산 내역(하위 주문마다 하나, 구매확정 최근 순). 주문 항목마다 받은 때 고정한 요율과 수수료를 보인다. 다음 쪽은 `?cursor=`다.
 */
export function SettlementItems({
  items,
  basePath,
  cursor,
  nextCursor,
}: {
  items: SettlementItem[];
  basePath: string;
  cursor: string | undefined;
  nextCursor: string | undefined;
}) {
  return (
    <section aria-labelledby="items-heading" className="grid gap-3">
      <h2 id="items-heading" className="text-lg font-semibold">
        정산 내역
      </h2>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {cursor ? "더 볼 내역이 없습니다." : "내역이 없습니다."}
        </p>
      ) : (
        <ul aria-label="정산 내역" className="grid gap-3">
          {items.map((item) => (
            <li key={item.sellerOrderId}>
              <Card>
                <CardContent className="grid gap-3 pt-6 text-sm">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <Link
                      href={`/orders/${item.sellerOrderId}`}
                      className="font-medium hover:underline"
                    >
                      {formatDateTime(item.confirmedAt)} 구매확정
                    </Link>
                    <span className="font-semibold tabular-nums">
                      정산 {formatPrice(item.settlementAmount)}
                    </span>
                  </div>
                  <ul className="grid gap-1">
                    {(item.lines ?? []).map((line) => (
                      <li key={line.orderLineId} className="flex justify-between gap-4">
                        <span>
                          {line.productName} × {line.quantity}
                        </span>
                        <span className="text-right tabular-nums text-muted-foreground">
                          {formatPrice(line.amount)} · 수수료 {rate(line.commissionRateBp)}{" "}
                          {formatPrice(line.commissionAmount)}
                        </span>
                      </li>
                    ))}
                  </ul>
                  {item.sellerCouponDiscount ? (
                    <p className="text-muted-foreground">
                      판매자 쿠폰 부담 -{formatPrice(item.sellerCouponDiscount)}
                    </p>
                  ) : null}
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
      <div className="flex justify-between">
        {cursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={basePath}>처음으로</Link>
          </Button>
        ) : (
          <span />
        )}
        {nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`${basePath}?cursor=${encodeURIComponent(nextCursor)}`}>다음</Link>
          </Button>
        ) : null}
      </div>
    </section>
  );
}
