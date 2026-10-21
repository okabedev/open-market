import type { OrderComponents } from "@openmarket/api-client";
import { Card, CardContent, CardHeader, CardTitle, formatPrice } from "@openmarket/ui";

type SellerOrder = OrderComponents["schemas"]["OrderSellerOrderView"];

/** 주문 상품을 판매자(하위 주문)별로 묶어 보여 주고 결제 금액을 더한다. 결제 완료 화면과 주문 상세가 쓴다. */
export function OrderItemsCard({
  sellerOrders,
  amount,
}: {
  sellerOrders: SellerOrder[] | undefined;
  amount: number | undefined;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">주문 상품</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm">
        {(sellerOrders ?? []).map((sellerOrder) => (
          <section key={sellerOrder.sellerOrderId} className="grid gap-1">
            <h2 className="font-medium">{sellerOrder.storeName}</h2>
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
