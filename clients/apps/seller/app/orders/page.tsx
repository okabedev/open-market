import Link from "next/link";
import {
  Alert,
  Card,
  formatDateTime,
  formatPrice,
  listQuery,
  OrderStatusBadge,
  PAGE_SIZE,
  PageHeader,
  Pager,
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
 * 본인 하위 주문 목록(ORD-4, 조회만). 결제된 주문만 최근 결제 순으로 보인다(order.md "조회"). 발주 확인·발송은 M4 fulfillment에서
 * 한다.
 */
export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  await requireSeller("/orders");
  const { page } = listQuery(await searchParams, [], undefined);
  const order = await bff.serviceClient("order");
  const { data, response } = await order.GET("/v1/seller/orders", {
    params: { query: { page, size: PAGE_SIZE } },
  });
  return (
    <>
      <PageHeader title="주문" description="결제된 주문만 최근 결제 순으로 보입니다." />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>결제된 주문이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>결제 시각</TableHead>
                <TableHead>상품</TableHead>
                <TableHead className="text-right">금액</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>주문 시각</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((item) => (
                <TableRow key={item.sellerOrderId}>
                  <TableCell className="whitespace-nowrap">{formatDateTime(item.paidAt)}</TableCell>
                  <TableCell>
                    <Link
                      href={`/orders/${item.sellerOrderId}`}
                      className="font-medium hover:underline"
                    >
                      {item.firstProductName}
                      {(item.lineCount ?? 1) > 1 ? ` 외 ${(item.lineCount ?? 1) - 1}건` : ""}
                    </Link>
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.amount)}
                  </TableCell>
                  <TableCell>
                    <OrderStatusBadge status={item.status} />
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.placedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath="/orders"
          status={undefined}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
