import {
  Alert,
  Button,
  Card,
  formatDateTime,
  formatPrice,
  OrderStatusBadge,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { cancelReasonMessage } from "@/lib/checkout";
import { requireSession } from "@/lib/session";

const SIZE = 20;

/** 본인 주문 목록(ORD-3). 모든 상태를 최근 주문 순으로 보여 주고, 커서로 다음 쪽을 연다(order.md "조회"). */
export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  await requireSession("/orders");
  const { cursor } = await searchParams;
  const current = typeof cursor === "string" && cursor ? cursor : undefined;
  const order = await bff.serviceClient("order");
  const { data, response } = await order.GET("/v1/orders", {
    params: { query: { size: SIZE, ...(current ? { cursor: current } : {}) } },
  });
  const items = data?.items ?? [];
  return (
    <>
      <PageHeader title="주문 내역" description="최근 주문 순입니다." />
      {!response.ok || !data ? (
        <Alert variant="destructive">주문 내역을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>
          주문한 상품이 없습니다.{" "}
          <Link href="/products" className="font-medium underline">
            상품 보러 가기
          </Link>
        </Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>주문 시각</TableHead>
                <TableHead>주문</TableHead>
                <TableHead className="text-right">금액</TableHead>
                <TableHead>상태</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.orderId}>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.placedAt)}
                  </TableCell>
                  <TableCell>
                    <Link href={`/orders/${item.orderId}`} className="font-medium hover:underline">
                      {item.orderName}
                    </Link>
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.amount)}
                  </TableCell>
                  <TableCell>
                    <div className="grid justify-items-start gap-1">
                      <OrderStatusBadge status={item.status} />
                      {item.status === "CANCELLED" ? (
                        <span className="text-xs text-muted-foreground">
                          {cancelReasonMessage(item.cancelReason)}
                        </span>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <div className="mt-4 flex justify-between">
        {current ? (
          <Button asChild variant="outline" size="sm">
            <Link href="/orders">처음으로</Link>
          </Button>
        ) : (
          <span />
        )}
        {data?.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/orders?cursor=${encodeURIComponent(data.nextCursor)}`}>다음</Link>
          </Button>
        ) : null}
      </div>
    </>
  );
}
