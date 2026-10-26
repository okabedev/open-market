import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  formatDateTime,
  PageHeader,
  ProductImage,
  stars,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";
import { ReplyForm } from "./reply-form";

const PAGE_SIZE = 20;

/**
 * 내 상품 리뷰(REV-3). 작성 최근 순 커서 목록이고 다음 쪽은 `?cursor=`다. 관리자가 숨긴 리뷰도 "숨김"으로 보인다. 리뷰마다 답글을 하나
 * 달고 고칠 수 있다. 구매자 이름은 review에 없어 보이지 않는다. 새 리뷰 알림은 `#review-<리뷰 ID>`로 이 목록의 그 리뷰로 온다.
 */
export default async function ReviewsPage({ searchParams }: PageProps<"/reviews">) {
  await requireSeller("/reviews");
  const { cursor, notice } = await searchParams;
  const review = await bff.serviceClient("review");
  const { data } = await review
    .GET("/v1/seller/reviews", {
      params: { query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) } },
    })
    .catch(() => ({ data: undefined }));
  const items = data?.items ?? [];
  return (
    <>
      <PageHeader
        title="리뷰"
        description="구매확정한 구매자가 쓴 내 상품 리뷰입니다. 리뷰마다 답글을 하나 달 수 있고, 답글은 고칠 수 있습니다."
      />
      {notice === "replied" ? (
        <Alert variant="success" className="mb-4">
          답글을 저장했습니다.
        </Alert>
      ) : null}
      {!data ? (
        <Alert variant="destructive">리뷰를 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>
          {typeof cursor === "string" ? "더 볼 리뷰가 없습니다." : "아직 리뷰가 없습니다."}
        </Alert>
      ) : (
        <ul aria-label="리뷰" className="grid gap-4">
          {items.map((item) => (
            <li
              key={item.reviewId}
              id={`review-${item.reviewId}`}
              className="scroll-mt-20"
              aria-label={`${item.productName} 리뷰`}
            >
              <Card>
                <CardContent className="grid gap-3 pt-6 text-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link
                      href={`/products/${item.productId}`}
                      className="font-medium hover:underline"
                    >
                      {item.productName}
                    </Link>
                    {(item.optionValues ?? []).length > 0 ? (
                      <span className="text-muted-foreground">
                        {(item.optionValues ?? []).join(" / ")}
                      </span>
                    ) : null}
                    {item.status === "HIDDEN" ? <Badge variant="secondary">숨김</Badge> : null}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-primary" aria-label={`별점 ${item.rating}점`}>
                      {stars(item.rating ?? 0)}
                    </span>
                    <time className="text-muted-foreground" dateTime={item.createdAt}>
                      {formatDateTime(item.createdAt)}
                    </time>
                  </div>
                  {item.status === "HIDDEN" ? (
                    <p className="text-xs text-muted-foreground">
                      관리자가 숨긴 리뷰입니다. 상품 상세와 별점에 보이지 않습니다.
                    </p>
                  ) : null}
                  <p className="whitespace-pre-wrap leading-relaxed">{item.content}</p>
                  {(item.fileIds ?? []).length > 0 ? (
                    <ul aria-label="리뷰 사진" className="flex flex-wrap gap-2">
                      {(item.fileIds ?? []).map((fileId, index) => (
                        <li key={fileId}>
                          <a href={`/files/${fileId}/w800`} target="_blank" rel="noreferrer">
                            <ProductImage
                              fileId={fileId}
                              size={96}
                              alt={`리뷰 사진 ${index + 1}`}
                            />
                          </a>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                  {item.reply ? (
                    <div className="grid gap-1 rounded-md bg-muted p-3">
                      <span className="font-medium">내 답글</span>
                      <p className="whitespace-pre-wrap">{item.reply.content}</p>
                      <span className="text-xs text-muted-foreground">
                        {formatDateTime(item.reply.updatedAt ?? item.reply.createdAt)}
                      </span>
                    </div>
                  ) : null}
                  {item.reviewId ? (
                    <ReplyForm reviewId={item.reviewId} current={item.reply?.content} />
                  ) : null}
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4 flex justify-between">
        {typeof cursor === "string" ? (
          <Button asChild variant="outline" size="sm">
            <Link href="/reviews">처음으로</Link>
          </Button>
        ) : (
          <span />
        )}
        {data?.nextCursor ? (
          <Button asChild variant="outline" size="sm">
            <Link href={`/reviews?cursor=${encodeURIComponent(data.nextCursor)}`}>다음</Link>
          </Button>
        ) : null}
      </div>
    </>
  );
}
