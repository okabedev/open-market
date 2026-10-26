import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatDateTime,
  PageHeader,
  ProductImage,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { stars } from "@/lib/reviews";
import { requireSession } from "@/lib/session";

const PAGE_SIZE = 20;

type Query = Record<string, string | string[] | undefined>;

/** 지금 조건에서 한 목록의 커서만 바꾼 주소. 다른 목록의 쪽은 그대로 둔다. */
function pageHref(query: Query, name: "writableCursor" | "cursor", cursor?: string): string {
  const next = new URLSearchParams();
  for (const key of ["writableCursor", "cursor"] as const) {
    const value = key === name ? cursor : query[key];
    if (typeof value === "string" && value) {
      next.set(key, value);
    }
  }
  const search = next.toString();
  return search ? `/my/reviews?${search}` : "/my/reviews";
}

function options(values: string[] | undefined): string {
  return (values ?? []).length > 0 ? (values ?? []).join(" / ") : "";
}

/**
 * 내 리뷰(REV-1). 위는 리뷰를 쓸 수 있는 구매확정 주문 상품(구매확정 최근 순), 아래는 내가 쓴 리뷰(작성 최근 순)다. 숨긴 리뷰도 "숨김"으로
 * 보인다. 목록마다 커서(`?writableCursor=`, `?cursor=`)로 다음 쪽을 넘긴다. 구매확정은 이벤트로 review에 닿으므로 잠시 뒤 보인다.
 */
export default async function MyReviewsPage({ searchParams }: PageProps<"/my/reviews">) {
  const query = await searchParams;
  const { writableCursor, cursor, notice } = query;
  await requireSession("/my/reviews");
  const review = await bff.serviceClient("review");
  const [writable, written] = await Promise.all([
    review
      .GET("/v1/reviews/writable", {
        params: {
          query: {
            size: PAGE_SIZE,
            ...(typeof writableCursor === "string" ? { cursor: writableCursor } : {}),
          },
        },
      })
      .catch(() => ({ data: undefined })),
    review
      .GET("/v1/reviews", {
        params: {
          query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) },
        },
      })
      .catch(() => ({ data: undefined })),
  ]);
  const writableItems = writable.data?.items ?? [];
  const writtenItems = written.data?.items ?? [];
  return (
    <>
      <PageHeader
        title="내 리뷰"
        description="구매확정한 상품마다 리뷰를 한 번 쓸 수 있습니다. 쓴 리뷰는 고치거나 지울 수 없습니다."
      />
      <div className="grid gap-6">
        {notice === "review-written" ? <Alert variant="success">리뷰를 등록했습니다.</Alert> : null}

        <Card>
          <CardHeader>
            <CardTitle>쓸 수 있는 리뷰</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            {!writable.data ? (
              <Alert variant="destructive">쓸 수 있는 리뷰를 불러오지 못했습니다.</Alert>
            ) : writableItems.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                리뷰를 쓸 상품이 없습니다. 구매확정한 상품은 잠시 뒤 여기에 보입니다.
              </p>
            ) : (
              <ul aria-label="쓸 수 있는 리뷰" className="divide-y text-sm">
                {writableItems.map((item) => (
                  <li
                    key={item.orderLineId}
                    className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0"
                  >
                    <span className="grid gap-0.5">
                      <Link
                        href={`/products/${item.productId}`}
                        className="font-medium hover:underline"
                      >
                        {item.productName}
                      </Link>
                      {options(item.optionValues) ? (
                        <span className="text-muted-foreground">{options(item.optionValues)}</span>
                      ) : null}
                      <span className="text-xs text-muted-foreground">
                        {formatDateTime(item.confirmedAt)} 구매확정
                      </span>
                    </span>
                    <Button asChild size="sm">
                      <Link
                        href={`/orders/${item.orderId}/reviews/${item.orderLineId}`}
                        aria-label={`${item.productName} 리뷰 쓰기`}
                      >
                        리뷰 쓰기
                      </Link>
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            {writable.data?.nextCursor ? (
              <div className="flex justify-end">
                <Button asChild variant="outline" size="sm">
                  <Link href={pageHref(query, "writableCursor", writable.data.nextCursor)}>
                    다음
                  </Link>
                </Button>
              </div>
            ) : typeof writableCursor === "string" ? (
              <div>
                <Button asChild variant="outline" size="sm">
                  <Link href={pageHref(query, "writableCursor")}>처음으로</Link>
                </Button>
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>내가 쓴 리뷰</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            {!written.data ? (
              <Alert variant="destructive">내가 쓴 리뷰를 불러오지 못했습니다.</Alert>
            ) : writtenItems.length === 0 ? (
              <p className="text-sm text-muted-foreground">아직 쓴 리뷰가 없습니다.</p>
            ) : (
              <ul aria-label="내가 쓴 리뷰" className="divide-y text-sm">
                {writtenItems.map((item) => (
                  <li key={item.reviewId} className="grid gap-2 py-4 first:pt-0 last:pb-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/products/${item.productId}`}
                        className="font-medium hover:underline"
                      >
                        {item.productName}
                      </Link>
                      {options(item.optionValues) ? (
                        <span className="text-muted-foreground">{options(item.optionValues)}</span>
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
                        운영 정책에 따라 숨긴 리뷰입니다. 상품 상세와 별점에 보이지 않습니다.
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
                        <span className="font-medium">판매자 답글</span>
                        <p className="whitespace-pre-wrap">{item.reply.content}</p>
                      </div>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
            {written.data?.nextCursor ? (
              <div className="flex justify-end">
                <Button asChild variant="outline" size="sm">
                  <Link href={pageHref(query, "cursor", written.data.nextCursor)}>다음</Link>
                </Button>
              </div>
            ) : typeof cursor === "string" ? (
              <div>
                <Button asChild variant="outline" size="sm">
                  <Link href={pageHref(query, "cursor")}>처음으로</Link>
                </Button>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
