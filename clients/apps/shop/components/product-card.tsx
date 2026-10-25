import { Badge, formatPrice, ProductImage } from "@openmarket/ui";
import Link from "next/link";
import { ratingLabel } from "@/lib/reviews";

type ProductCardProps = {
  productId?: string;
  name?: string;
  storeName?: string;
  minPrice?: number;
  inStock?: boolean;
  thumbnailFileId?: string;
  ratingAverage?: number;
  ratingCount?: number;
};

/** 검색 결과 상품 하나. 품절이면 표시만 하고 링크는 둔다(상세에서 옵션별로 볼 수 있다). 리뷰가 있으면 평균 별점과 리뷰 수를 보인다. */
export function ProductCard({
  productId,
  name,
  storeName,
  minPrice,
  inStock,
  thumbnailFileId,
  ratingAverage,
  ratingCount,
}: ProductCardProps) {
  return (
    <Link
      href={`/products/${productId}`}
      className="group grid gap-2 rounded-xl border bg-card p-3 hover:border-primary"
    >
      <ProductImage
        fileId={thumbnailFileId}
        size={300}
        alt={name ?? "상품"}
        className="aspect-square h-auto! w-full!"
      />
      <div className="grid gap-1">
        <span className="text-xs text-muted-foreground">{storeName}</span>
        <span className="line-clamp-2 text-sm font-medium group-hover:underline">{name}</span>
        <div className="flex items-center gap-2">
          <span className="font-semibold">{formatPrice(minPrice)}</span>
          {inStock === false ? <Badge variant="secondary">품절</Badge> : null}
        </div>
        {ratingCount ? (
          <span className="text-xs text-muted-foreground">
            <span aria-hidden className="text-primary">
              ★{" "}
            </span>
            {ratingLabel(ratingAverage, ratingCount)}
          </span>
        ) : null}
      </div>
    </Link>
  );
}
