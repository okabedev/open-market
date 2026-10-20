import Link from "next/link";
import {
  Alert,
  Button,
  Card,
  formatDateTime,
  listQuery,
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
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";

/** SKU 하나의 재고 조정 이력(CAT-3). 최근 순, 쪽 번호. */
export default async function StockAdjustmentsPage({
  params,
  searchParams,
}: PageProps<"/products/[productId]/skus/[skuId]/stock-adjustments">) {
  const { productId, skuId } = await params;
  const basePath = `/products/${productId}/skus/${skuId}/stock-adjustments`;
  await requireSeller(basePath);
  const { page } = listQuery(await searchParams, [], undefined);
  const catalog = await bff.serviceClient("catalog");
  const [{ data: product }, { data, response }] = await Promise.all([
    catalog.GET("/v1/seller/products/{productId}", { params: { path: { productId } } }),
    catalog.GET("/v1/seller/products/{productId}/skus/{skuId}/stock-adjustments", {
      params: { path: { productId, skuId }, query: { page, size: PAGE_SIZE } },
    }),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  const sku = product?.skus?.find((item) => item.skuId === skuId);
  const label = (sku?.optionValues ?? []).join(" / ") || "기본";
  return (
    <>
      <PageHeader
        title="재고 조정 이력"
        description={`${product?.name ?? ""} · ${label}`}
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href={`/products/${productId}`}>상품으로</Link>
          </Button>
        }
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">이력을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>조정한 적이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>시각</TableHead>
                <TableHead className="text-right">증감</TableHead>
                <TableHead className="text-right">조정 뒤 실재고</TableHead>
                <TableHead>사유</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((item) => (
                <TableRow key={item.adjustmentId}>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.adjustedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    {(item.delta ?? 0) > 0 ? `+${item.delta}` : item.delta}
                  </TableCell>
                  <TableCell className="text-right">
                    {item.onHandAfter?.toLocaleString("ko-KR")}
                  </TableCell>
                  <TableCell>{item.reason ?? "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath={basePath}
          status={undefined}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
