import {
  Alert,
  Badge,
  formatPrice,
  ProductImage,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { notFound } from "next/navigation";
import { bff } from "@/lib/bff";

/**
 * 구매자 상품 상세. 판매 중·판매 중지 상품만 보이고(제재·등록 중은 404), 남은 수량 대신 옵션별 품절 여부를 보여 준다. 주문은 M3에서 연다.
 */
export default async function ProductPage({ params }: PageProps<"/products/[productId]">) {
  const { productId } = await params;
  const catalog = await bff.publicClient("catalog");
  const { data: product, response } = await catalog.GET("/v1/products/{productId}", {
    params: { path: { productId } },
  });
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!product) {
    return <Alert variant="destructive">상품을 불러오지 못했습니다.</Alert>;
  }
  const images = product.imageFileIds ?? [];
  const skus = product.skus ?? [];
  const optionNames = product.optionNames ?? [];
  const prices = new Set(skus.map((sku) => sku.price));
  const stopped = product.status === "STOPPED";
  return (
    <article className="grid gap-8">
      <nav
        aria-label="카테고리 경로"
        className="flex flex-wrap gap-1 text-sm text-muted-foreground"
      >
        {(product.categoryPath ?? []).map((category, index) => (
          <span key={category.categoryId}>
            {index > 0 ? " > " : ""}
            <Link href={`/products?categoryId=${category.categoryId}`} className="hover:underline">
              {category.name}
            </Link>
          </span>
        ))}
      </nav>
      <div className="grid gap-8 md:grid-cols-2">
        <div className="grid content-start gap-3">
          <ProductImage
            fileId={images[0]}
            variant="w800"
            size={800}
            alt={product.name ?? "상품"}
            className="aspect-square h-auto! w-full!"
          />
          {images.length > 1 ? (
            <ul className="flex flex-wrap gap-2">
              {images.slice(1).map((fileId, index) => (
                <li key={fileId}>
                  <ProductImage
                    fileId={fileId}
                    size={80}
                    alt={`${product.name} 이미지 ${index + 2}`}
                  />
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        <div className="grid content-start gap-4">
          <p className="text-sm text-muted-foreground">{product.storeName}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{product.name}</h1>
          <p className="text-2xl font-bold">
            {formatPrice(product.minPrice)}
            {prices.size > 1 ? <span className="text-base font-normal">부터</span> : null}
          </p>
          {stopped ? (
            <Alert>판매가 중지된 상품입니다.</Alert>
          ) : !product.inStock ? (
            <Alert>모든 옵션이 품절입니다.</Alert>
          ) : null}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{optionNames.length > 0 ? optionNames.join(" / ") : "옵션"}</TableHead>
                <TableHead className="text-right">가격</TableHead>
                <TableHead className="text-right">재고</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {skus.map((sku) => (
                <TableRow key={sku.skuId}>
                  <TableCell>{(sku.optionValues ?? []).join(" / ") || "기본"}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(sku.price)}
                  </TableCell>
                  <TableCell className="text-right">
                    {sku.inStock ? (
                      <Badge variant="success">구매 가능</Badge>
                    ) : (
                      <Badge variant="secondary">품절</Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="text-sm text-muted-foreground">
            장바구니와 주문은 다음 마일스톤에서 열립니다.
          </p>
        </div>
      </div>
      <section className="grid gap-2">
        <h2 className="text-lg font-semibold">상품 설명</h2>
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{product.description}</p>
      </section>
    </article>
  );
}
