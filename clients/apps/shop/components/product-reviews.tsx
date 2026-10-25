import { Alert, Button, ProductImage } from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { ratingLabel, stars } from "@/lib/reviews";

const PAGE_SIZE = 10;

/**
 * 상품 상세의 별점 요약과 리뷰(REV-2). 보이는 리뷰만 최근 순으로 10개씩 보이고, 다음 쪽은 `?reviewCursor=`로 넘긴다. 작성자는 보이지 않는다
 * (review는 구매자 이름을 갖지 않는다, 2026-10-26 결정). 고른 옵션, 사진, 판매자 답글을 함께 보인다. review에 닿지 못하면 이 칸만 안내한다.
 */
export async function ProductReviews({
  productId,
  cursor,
}: {
  productId: string;
  cursor?: string;
}) {
  const review = await bff.publicClient("review");
  const [summary, page] = await Promise.all([
    review
      .GET("/v1/products/{productId}/rating-summary", { params: { path: { productId } } })
      .catch(() => null),
    review
      .GET("/v1/products/{productId}/reviews", {
        params: { path: { productId }, query: { size: PAGE_SIZE, ...(cursor ? { cursor } : {}) } },
      })
      .catch(() => null),
  ]);
  const rating = summary?.response.ok ? summary.data : undefined;
  const reviews = page?.response.ok ? page.data : undefined;
  const items = reviews?.items ?? [];
  return (
    <section id="reviews" className="grid gap-4" aria-labelledby="reviews-heading">
      <div className="flex flex-wrap items-baseline gap-3">
        <h2 id="reviews-heading" className="text-lg font-semibold">
          리뷰
        </h2>
        {rating ? (
          <p className="text-sm">
            {rating.ratingCount ? (
              <span aria-hidden className="mr-1 text-primary">
                {stars(rating.ratingAverage ?? 0)}
              </span>
            ) : null}
            {ratingLabel(rating.ratingAverage, rating.ratingCount)}
          </p>
        ) : null}
      </div>
      {!reviews ? (
        <Alert variant="destructive">리뷰를 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {cursor ? "더 볼 리뷰가 없습니다." : "아직 리뷰가 없습니다."}
        </p>
      ) : (
        <ul className="grid gap-4">
          {items.map((item) => (
            <li key={item.reviewId} className="grid gap-2 border-b pb-4 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-primary" aria-label={`별점 ${item.rating}점`}>
                  {stars(item.rating ?? 0)}
                </span>
                {(item.optionValues ?? []).length > 0 ? (
                  <span className="text-muted-foreground">
                    {(item.optionValues ?? []).join(" / ")}
                  </span>
                ) : null}
                <time className="text-muted-foreground" dateTime={item.createdAt}>
                  {formatDate(item.createdAt)}
                </time>
              </div>
              <p className="whitespace-pre-wrap leading-relaxed">{item.content}</p>
              {(item.fileIds ?? []).length > 0 ? (
                <ul className="flex flex-wrap gap-2">
                  {(item.fileIds ?? []).map((fileId, index) => (
                    <li key={fileId}>
                      <a href={`/files/${fileId}/w800`} target="_blank" rel="noreferrer">
                        <ProductImage fileId={fileId} size={96} alt={`리뷰 사진 ${index + 1}`} />
                      </a>
                    </li>
                  ))}
                </ul>
              ) : null}
              {item.reply ? (
                <div className="grid gap-1 rounded-md bg-muted p-3">
                  <span className="font-medium">판매자 답글</span>
                  <p className="whitespace-pre-wrap">{item.reply.content}</p>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <div className="flex justify-between">
        {cursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/products/${productId}#reviews`}>처음 리뷰로</Link>
          </Button>
        ) : (
          <span />
        )}
        {reviews?.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link
              href={`/products/${productId}?reviewCursor=${encodeURIComponent(reviews.nextCursor)}#reviews`}
            >
              리뷰 더 보기
            </Link>
          </Button>
        ) : null}
      </div>
    </section>
  );
}

function formatDate(value: string | undefined): string {
  return value
    ? new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium" }).format(
        new Date(value),
      )
    : "";
}
