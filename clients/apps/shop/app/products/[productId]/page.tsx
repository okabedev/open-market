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
import { randomUUID } from "node:crypto";
import { addToCart } from "@/app/cart/actions";
import { buyNow } from "@/app/checkout/actions";
import { QuantityForm } from "@/components/quantity-form";
import { bff } from "@/lib/bff";
import { currentSession } from "@/lib/session";

/**
 * 구매자 상품 상세. 판매 중·판매 중지 상품만 보이고(제재·등록 중은 404), 남은 수량 대신 옵션별 품절 여부를 보여 준다. 로그인하면 판매
 * 중인 상품의 품절 아닌 옵션을 장바구니에 담거나 바로 구매한다(재고는 주문할 때 예약으로 정한다). 담으면 `?added=<SKU ID>`로 돌아와
 * 안내하고, 바로 구매는 그 SKU 하나로 주문서를 연다.
 */
export default async function ProductPage({
  params,
  searchParams,
}: PageProps<"/products/[productId]">) {
  const { productId } = await params;
  const { added } = await searchParams;
  const session = await currentSession();
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
  const addedSku = skus.find((sku) => sku.skuId === added);
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
          {addedSku ? (
            <Alert variant="success">
              장바구니에 담았습니다({(addedSku.optionValues ?? []).join(" / ") || "기본"}).{" "}
              <Link href="/cart" className="font-medium underline">
                장바구니 보기
              </Link>
            </Alert>
          ) : null}
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
                {session && !stopped ? <TableHead className="text-right">구매</TableHead> : null}
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
                  {session && !stopped ? (
                    <TableCell className="text-right">
                      {sku.inStock ? (
                        <QuantityForm
                          action={addToCart}
                          // 옵션마다 화면을 그릴 때 한 번 만든다. 두 번 눌러도 한 번만 담긴다.
                          hidden={{
                            skuId: sku.skuId ?? "",
                            productId,
                            idempotencyKey: randomUUID(),
                          }}
                          id={`quantity-${sku.skuId}`}
                          label={`${(sku.optionValues ?? []).join(" / ") || "기본"} 수량`}
                          defaultQuantity={1}
                          submitText="담기"
                          secondary={{ action: buyNow, text: "바로 구매" }}
                        />
                      ) : (
                        <span className="text-sm text-muted-foreground">-</span>
                      )}
                    </TableCell>
                  ) : null}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {session ? null : (
            <p className="text-sm text-muted-foreground">
              <Link
                href={`/auth/login?returnTo=${encodeURIComponent(`/products/${productId}`)}`}
                className="font-medium underline"
              >
                로그인
              </Link>
              하면 장바구니에 담거나 바로 구매할 수 있습니다.
            </p>
          )}
        </div>
      </div>
      <section className="grid gap-2">
        <h2 className="text-lg font-semibold">상품 설명</h2>
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{product.description}</p>
      </section>
    </article>
  );
}
