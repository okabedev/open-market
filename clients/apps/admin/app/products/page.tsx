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
  PRODUCT_STATUSES,
  PRODUCT_STATUS_LABELS,
  ProductImage,
  ProductStatusBadge,
  StatusTabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { z } from "zod";
import { ReasonForm } from "@/components/reason-form";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { ban } from "./actions";

/** 관리자 상품 목록과 제재(CAT-5, ADM-2). 상태와 판매자로 거른다. 등록 중인 상품도 제재할 수 있다. */
export default async function ProductsPage({ searchParams }: PageProps<"/products">) {
  await requireSession("/products");
  const params = await searchParams;
  const { status, page } = listQuery(params, PRODUCT_STATUSES, undefined);
  const sellerParam = z.uuid().safeParse(params.sellerId);
  const sellerId = sellerParam.success ? sellerParam.data : undefined;
  const notice = noticeMessage(params.notice);
  const query = { sellerId };
  const returnTo = `/products?${new URLSearchParams({
    ...(sellerId ? { sellerId } : {}),
    ...(status ? { status } : {}),
    page: String(page),
  }).toString()}`;
  const catalog = await bff.serviceClient("catalog");
  const { data, response } = await catalog.GET("/v1/admin/products", {
    params: {
      query: {
        ...(status ? { status } : {}),
        ...(sellerId ? { sellerId } : {}),
        page,
        size: PAGE_SIZE,
      },
    },
  });
  const items = data?.items ?? [];
  return (
    <>
      <PageHeader
        title="상품"
        description="최근 수정 순입니다. 제재하면 구매자에게 보이지 않고, 판매자는 다시 열거나 고칠 수 없습니다."
        actions={
          sellerId ? (
            <Button asChild variant="outline" size="sm">
              <Link href={status ? `/products?status=${status}` : "/products"}>
                판매자 필터 해제
              </Link>
            </Button>
          ) : undefined
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      {sellerId && items[0]?.storeName ? (
        <p className="mb-4 text-sm text-muted-foreground">판매자: {items[0].storeName}</p>
      ) : null}
      <StatusTabs
        basePath="/products"
        current={status}
        query={query}
        tabs={[
          { status: undefined, label: "전체" },
          ...PRODUCT_STATUSES.map((value) => ({
            status: value,
            label: PRODUCT_STATUS_LABELS[value],
          })),
        ]}
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>상품이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상품</TableHead>
                <TableHead>판매자</TableHead>
                <TableHead>상태</TableHead>
                <TableHead className="text-right">최저가</TableHead>
                <TableHead>수정</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.productId}>
                  <TableCell>
                    <div className="flex items-center gap-3 font-medium">
                      <ProductImage fileId={item.thumbnailFileId} size={48} alt={item.name ?? ""} />
                      {item.name}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Link href={`/products?sellerId=${item.sellerId}`} className="hover:underline">
                      {item.storeName}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <div className="grid gap-1">
                      <ProductStatusBadge status={item.status} />
                      {item.banReason ? <span className="text-xs">{item.banReason}</span> : null}
                    </div>
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.minPrice)}
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.updatedAt)}
                  </TableCell>
                  <TableCell className="min-w-64">
                    {item.status === "BANNED" ? (
                      <span className="text-sm text-muted-foreground">제재됨</span>
                    ) : (
                      <details>
                        <summary className="cursor-pointer text-sm text-destructive">제재</summary>
                        <div className="pt-2">
                          <ReasonForm
                            action={ban}
                            hidden={{ productId: item.productId ?? "", returnTo }}
                            label="제재 사유"
                            submitText="제재"
                          />
                        </div>
                      </details>
                    )}
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
          query={query}
        />
      ) : null}
    </>
  );
}
