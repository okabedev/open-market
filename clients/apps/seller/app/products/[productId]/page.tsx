import Link from "next/link";
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ConfirmActionForm,
  formatDateTime,
  formatPrice,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  ProductImage,
  ProductStatusBadge,
} from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/notice";
import { STOP_REASON_LABELS } from "@/lib/product-form";
import { requireSeller } from "@/lib/session";
import { publishProduct, resumeProduct, stopProduct } from "../actions";
import { StockForm } from "./stock-form";

/** 본인 상품 상세. 판매 시작·중지·재개와 SKU별 재고 조정(CAT-2, CAT-3). */
export default async function ProductPage({
  params,
  searchParams,
}: PageProps<"/products/[productId]">) {
  const { productId } = await params;
  await requireSeller(`/products/${productId}`);
  const notice = noticeMessage((await searchParams).notice);
  const catalog = await bff.serviceClient("catalog");
  const { data: product, response } = await catalog.GET("/v1/seller/products/{productId}", {
    params: { path: { productId } },
  });
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!product) {
    return <Alert variant="destructive">상품을 불러오지 못했습니다.</Alert>;
  }
  const banned = product.status === "BANNED";
  const hidden = { productId };
  const optionNames = product.optionNames ?? [];
  return (
    <>
      <PageHeader
        title={product.name ?? "상품"}
        description={(product.categoryPath ?? []).map((category) => category.name).join(" > ")}
        actions={
          <div className="flex flex-wrap items-start gap-2">
            {!banned ? (
              <Button asChild variant="outline" size="sm">
                <Link href={`/products/${productId}/edit`}>수정</Link>
              </Button>
            ) : null}
            {product.status === "DRAFT" ? (
              <ConfirmActionForm
                action={publishProduct}
                hidden={hidden}
                title="판매를 시작할까요?"
                description="구매자 검색에 몇 초 안에 나옵니다."
                confirmText="판매 시작"
              >
                판매 시작
              </ConfirmActionForm>
            ) : null}
            {product.status === "ON_SALE" ? (
              <ConfirmActionForm
                action={stopProduct}
                hidden={hidden}
                title="판매를 멈출까요?"
                description="구매자 검색에서 빠지고, 상세 화면에는 판매 중지로 보입니다. 다시 시작할 수 있습니다."
                confirmText="판매 중지"
                variant="destructive"
              >
                판매 중지
              </ConfirmActionForm>
            ) : null}
            {product.status === "STOPPED" && product.stopReason === "SELLER" ? (
              <ConfirmActionForm
                action={resumeProduct}
                hidden={hidden}
                title="판매를 다시 시작할까요?"
                description="구매자 검색에 다시 나옵니다."
                confirmText="판매 재개"
              >
                판매 재개
              </ConfirmActionForm>
            ) : null}
          </div>
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      {banned ? (
        <Alert variant="destructive" className="mb-4">
          관리자가 판매를 제한했습니다. 사유: {product.banReason}. 수정과 판매 재개를 할 수
          없습니다. 고객센터로 문의해 주세요.
        </Alert>
      ) : null}
      {product.status === "STOPPED" && product.stopReason ? (
        <Alert className="mb-4">
          {STOP_REASON_LABELS[product.stopReason] ?? "판매를 멈췄습니다."}
        </Alert>
      ) : null}

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>상품 정보</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="flex flex-wrap gap-2">
              {(product.imageFileIds ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  이미지가 없습니다. 판매를 시작하려면 한 장 이상 넣어 주세요.
                </p>
              ) : (
                (product.imageFileIds ?? []).map((fileId, index) => (
                  <ProductImage
                    key={fileId}
                    fileId={fileId}
                    size={120}
                    alt={`이미지 ${index + 1}`}
                  />
                ))
              )}
            </div>
            <dl className="grid grid-cols-[8rem_1fr] gap-y-3 text-sm">
              <dt className="text-muted-foreground">상태</dt>
              <dd>
                <ProductStatusBadge status={product.status} />
              </dd>
              <dt className="text-muted-foreground">설명</dt>
              <dd className="whitespace-pre-wrap">{product.description}</dd>
              <dt className="text-muted-foreground">판매 시작</dt>
              <dd>{formatDateTime(product.publishedAt)}</dd>
              <dt className="text-muted-foreground">수정</dt>
              <dd>{formatDateTime(product.updatedAt)}</dd>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>옵션과 재고</CardTitle>
          </CardHeader>
          <Table>
            <TableHeader>
              <TableRow>
                {optionNames.length > 0 ? <TableHead>{optionNames.join(" / ")}</TableHead> : null}
                <TableHead className="text-right">가격</TableHead>
                <TableHead className="text-right">실재고</TableHead>
                <TableHead className="text-right">예약</TableHead>
                <TableHead className="text-right">판매 가능</TableHead>
                {!banned ? <TableHead>재고 조정</TableHead> : null}
                <TableHead>이력</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(product.skus ?? []).map((sku) => {
                const label = (sku.optionValues ?? []).join(" / ") || "기본";
                return (
                  <TableRow key={sku.skuId}>
                    {optionNames.length > 0 ? (
                      <TableCell className="font-medium">{label}</TableCell>
                    ) : null}
                    <TableCell className="text-right whitespace-nowrap">
                      {formatPrice(sku.price)}
                    </TableCell>
                    <TableCell className="text-right">
                      {sku.onHand?.toLocaleString("ko-KR")}
                    </TableCell>
                    <TableCell className="text-right">
                      {sku.reserved?.toLocaleString("ko-KR")}
                    </TableCell>
                    <TableCell className="text-right">
                      {sku.available === 0 ? (
                        <span className="text-destructive">품절</span>
                      ) : (
                        sku.available?.toLocaleString("ko-KR")
                      )}
                    </TableCell>
                    {!banned ? (
                      <TableCell className="min-w-80">
                        <StockForm
                          productId={productId}
                          skuId={sku.skuId ?? ""}
                          idempotencyKey={randomUUID()}
                          label={label}
                        />
                      </TableCell>
                    ) : null}
                    <TableCell>
                      <Link
                        href={`/products/${productId}/skus/${sku.skuId}/stock-adjustments`}
                        className="text-sm text-primary hover:underline"
                      >
                        보기
                      </Link>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>
      </div>
    </>
  );
}
