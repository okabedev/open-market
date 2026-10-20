import Link from "next/link";
import {
  Alert,
  Button,
  Card,
  formatDateTime,
  formatPrice,
  listQuery,
  PAGE_SIZE,
  PageHeader,
  Pager,
  StatusTabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import { ProductImage } from "@/components/product-image";
import { StatusBadge } from "@/components/status-badge";
import { bff } from "@/lib/bff";
import { STATUSES, STATUS_LABELS } from "@/lib/product-form";
import { requireSeller } from "@/lib/session";

/** 본인 상품 목록(최근 수정 순, 쪽 번호). 상태로 거른다. */
export default async function ProductsPage({ searchParams }: PageProps<"/products">) {
  await requireSeller("/products");
  const { status, page } = listQuery(await searchParams, STATUSES, undefined);
  const catalog = await bff.serviceClient("catalog");
  const { data, response } = await catalog.GET("/v1/seller/products", {
    params: { query: { ...(status ? { status } : {}), page, size: PAGE_SIZE } },
  });
  return (
    <>
      <PageHeader
        title="상품"
        description="최근 수정 순입니다. 등록한 상품은 판매를 시작해야 구매자에게 보입니다."
        actions={
          <Button asChild>
            <Link href="/products/new">상품 등록</Link>
          </Button>
        }
      />
      <StatusTabs
        basePath="/products"
        current={status}
        tabs={[
          { status: undefined, label: "전체" },
          ...STATUSES.map((value) => ({ status: value, label: STATUS_LABELS[value] })),
        ]}
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>상품이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상품</TableHead>
                <TableHead>상태</TableHead>
                <TableHead className="text-right">최저가</TableHead>
                <TableHead className="text-right">옵션 조합</TableHead>
                <TableHead className="text-right">판매 가능</TableHead>
                <TableHead>수정</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((item) => (
                <TableRow key={item.productId}>
                  <TableCell>
                    <Link
                      href={`/products/${item.productId}`}
                      className="flex items-center gap-3 font-medium hover:underline"
                    >
                      <ProductImage fileId={item.thumbnailFileId} size={48} alt={item.name ?? ""} />
                      {item.name}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={item.status} />
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.minPrice)}
                  </TableCell>
                  <TableCell className="text-right">{item.skuCount}</TableCell>
                  <TableCell className="text-right">
                    {item.available === 0 ? (
                      <span className="text-destructive">품절</span>
                    ) : (
                      item.available?.toLocaleString("ko-KR")
                    )}
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.updatedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath="/products"
          status={status}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
