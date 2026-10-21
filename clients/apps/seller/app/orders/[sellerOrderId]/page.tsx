import Link from "next/link";
import { notFound } from "next/navigation";
import {
  Alert,
  Button,
  Card,
  CardContent,
  formatDateTime,
  formatPrice,
  OrderStatusBadge,
  PageHeader,
  ShippingCard,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";

/**
 * 본인 하위 주문 상세(ORD-4, 조회만). 항목과 배송지를 보여 준다. 판매자는 다른 판매자의 항목, 주문 전체 금액, 구매자를 보지 않는다
 * (order.md "조회").
 */
export default async function SellerOrderPage({ params }: PageProps<"/orders/[sellerOrderId]">) {
  const { sellerOrderId } = await params;
  await requireSeller(`/orders/${sellerOrderId}`);
  const order = await bff.serviceClient("order");
  const { data, response } = await order.GET("/v1/seller/orders/{sellerOrderId}", {
    params: { path: { sellerOrderId } },
  });
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">주문을 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      <PageHeader
        title={`주문 ${data.orderId}`}
        description={`결제 시각 ${formatDateTime(data.paidAt)} · 주문 시각 ${formatDateTime(data.placedAt)}`}
        actions={
          <Button asChild variant="outline">
            <Link href="/orders">목록</Link>
          </Button>
        }
      />
      <div className="grid gap-4">
        <Card>
          <CardContent className="flex items-center gap-2 pt-6 text-sm">
            <span className="text-muted-foreground">상태</span>
            <OrderStatusBadge status={data.status} />
          </CardContent>
        </Card>
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상품</TableHead>
                <TableHead>옵션</TableHead>
                <TableHead className="text-right">개당 가격</TableHead>
                <TableHead className="text-right">수량</TableHead>
                <TableHead className="text-right">금액</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.lines ?? []).map((line) => (
                <TableRow key={line.orderLineId}>
                  <TableCell>
                    <Link
                      href={`/products/${line.productId}`}
                      className="font-medium hover:underline"
                    >
                      {line.productName}
                    </Link>
                  </TableCell>
                  <TableCell>{(line.optionValues ?? []).join(" / ") || "-"}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(line.unitPrice)}
                  </TableCell>
                  <TableCell className="text-right">{line.quantity}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(line.amount)}
                  </TableCell>
                </TableRow>
              ))}
              <TableRow>
                <TableCell colSpan={4} className="text-right font-semibold">
                  하위 주문 금액
                </TableCell>
                <TableCell className="text-right font-semibold whitespace-nowrap">
                  {formatPrice(data.amount)}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Card>
        <ShippingCard shipping={data.shipping} />
      </div>
    </>
  );
}
