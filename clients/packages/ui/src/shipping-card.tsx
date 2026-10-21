import { Card, CardContent, CardHeader, CardTitle } from "./card";

export type ShippingAddress = {
  recipientName?: string;
  recipientPhone?: string;
  zipCode?: string;
  baseAddress?: string;
  detailAddress?: string;
};

/** 주문 배송지. 구매자 주문 화면과 판매자 하위 주문 상세가 쓴다. */
export function ShippingCard({ shipping }: { shipping: ShippingAddress | undefined }) {
  if (!shipping) {
    return null;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">배송지</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-1 text-sm">
        <p>
          {shipping.recipientName} · {shipping.recipientPhone}
        </p>
        <p className="text-muted-foreground">
          ({shipping.zipCode}) {shipping.baseAddress} {shipping.detailAddress}
        </p>
      </CardContent>
    </Card>
  );
}
