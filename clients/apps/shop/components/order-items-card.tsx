import type { OrderComponents } from "@openmarket/api-client";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ClaimBadge,
  formatPrice,
  ProgressBadge,
} from "@openmarket/ui";
import Link from "next/link";

type SellerOrder = OrderComponents["schemas"]["OrderSellerOrderView"];

/**
 * 주문 상품을 판매자(하위 주문)별로 묶어 보여 주고 결제 금액을 더한다. 결제 완료 화면과 주문 상세가 쓴다. `orderId`를 주면(결제된 주문의
 * 상세) 하위 주문마다 진행 상태와 배송 조회 링크를 붙인다.
 */
export function OrderItemsCard({
  sellerOrders,
  amount,
  orderId,
}: {
  sellerOrders: SellerOrder[] | undefined;
  amount: number | undefined;
  orderId?: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">주문 상품</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm">
        {(sellerOrders ?? []).map((sellerOrder) => (
          <section key={sellerOrder.sellerOrderId} className="grid gap-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="font-medium">{sellerOrder.storeName}</h2>
              {orderId && sellerOrder.progress ? (
                <>
                  <ProgressBadge progress={sellerOrder.progress} />
                  <ClaimBadge claim={sellerOrder.claim} />
                  <Link
                    href={`/orders/${orderId}/seller-orders/${sellerOrder.sellerOrderId}`}
                    className="ml-auto text-sm font-medium underline"
                    aria-label={`${sellerOrder.storeName} 배송 조회`}
                  >
                    배송 조회
                  </Link>
                </>
              ) : null}
            </div>
            <ul className="grid gap-1">
              {(sellerOrder.lines ?? []).map((line) => (
                <li key={line.orderLineId} className="flex justify-between gap-4">
                  <span>
                    {line.productName}
                    {(line.optionValues ?? []).length > 0
                      ? ` (${(line.optionValues ?? []).join(" / ")})`
                      : ""}{" "}
                    × {line.quantity}
                  </span>
                  <span>{formatPrice(line.amount)}</span>
                </li>
              ))}
            </ul>
          </section>
        ))}
        <p className="flex justify-between border-t pt-3 text-base font-semibold">
          <span>결제 금액</span>
          <span>{formatPrice(amount)}</span>
        </p>
      </CardContent>
    </Card>
  );
}
