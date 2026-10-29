import Link from "next/link";
import {
  Alert,
  Card,
  ClaimBadge,
  formatDateTime,
  formatPrice,
  listQuery,
  PAGE_SIZE,
  PageHeader,
  Pager,
  ProgressBadge,
  StatusTabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { ORDER_TAB_KEYS, ORDER_TABS, orderTabFilter } from "@/lib/order-tabs";
import { requireSeller } from "@/lib/session";

/**
 * 본인 하위 주문 목록(ORD-4). 결제된 주문만 최근 결제 순으로 보인다(order.md "조회"). 탭으로 발주 확인·발송·클레임처럼 할 일이 남은
 * 것만 거른다. 처리는 상세에서 한다.
 */
export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  await requireSeller("/orders");
  const { status: tab, page } = listQuery(await searchParams, ORDER_TAB_KEYS, undefined);
  const order = await bff.serviceClient("order");
  const { data, response } = await order.GET("/v1/seller/orders", {
    params: { query: { ...orderTabFilter(tab), page, size: PAGE_SIZE } },
  });
  return (
    <>
      <PageHeader title="주문" description="결제된 주문만 최근 결제 순으로 보입니다." />
      <StatusTabs
        basePath="/orders"
        current={tab}
        tabs={[
          { status: undefined, label: "전체" },
          ...ORDER_TAB_KEYS.map((key) => ({ status: key, label: ORDER_TABS[key].label })),
        ]}
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>{tab ? "해당하는 주문이 없습니다." : "결제된 주문이 없습니다."}</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>결제 시각</TableHead>
                <TableHead>상품</TableHead>
                <TableHead className="text-right">금액</TableHead>
                <TableHead>진행 상태</TableHead>
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
                    {(item.sellerCouponDiscount ?? 0) > 0 ||
                    (item.platformCouponDiscount ?? 0) > 0 ? (
                      <div className="text-xs text-muted-foreground">
                        결제 {formatPrice(item.paymentAmount)}
                      </div>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      <ProgressBadge progress={item.progress} />
                      <ClaimBadge claim={item.claim} />
                    </div>
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
          status={tab}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
