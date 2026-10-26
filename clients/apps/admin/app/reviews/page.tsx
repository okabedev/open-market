import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  formatDateTime,
  listQuery,
  PAGE_SIZE,
  PageHeader,
  Pager,
  ProductImage,
  StatusTabs,
  stars,
} from "@openmarket/ui";
import Link from "next/link";
import { z } from "zod";
import { ReasonForm } from "@/components/reason-form";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { hideReview, showReview } from "./actions";

const STATUSES = ["VISIBLE", "HIDDEN"] as const;

/**
 * 리뷰 관리(REV-3, ADM-2). 모든 리뷰를 최근 작성 순, 쪽 번호로 보인다. 상태, 상품, 판매자로 거른다. 사유(1~200자)를 남겨 숨기거나 다시
 * 보인다. 사유는 감사 로그에만 남고 리뷰에는 저장하지 않는다. 구매자 이름은 review에 없어 보이지 않는다.
 */
export default async function ReviewsPage({ searchParams }: PageProps<"/reviews">) {
  await requireSession("/reviews");
  const params = await searchParams;
  const { status, page } = listQuery(params, STATUSES, undefined);
  const productParam = z.uuid().safeParse(params.productId);
  const productId = productParam.success ? productParam.data : undefined;
  const sellerParam = z.uuid().safeParse(params.sellerId);
  const sellerId = sellerParam.success ? sellerParam.data : undefined;
  const notice = noticeMessage(params.notice);
  const filters = { ...(productId ? { productId } : {}), ...(sellerId ? { sellerId } : {}) };
  const returnTo = `/reviews?${new URLSearchParams({
    ...filters,
    ...(status ? { status } : {}),
    page: String(page),
  }).toString()}`;
  const review = await bff.serviceClient("review");
  const { data, response } = await review
    .GET("/v1/admin/reviews", {
      params: { query: { ...filters, ...(status ? { status } : {}), page, size: PAGE_SIZE } },
    })
    .catch(() => ({ data: undefined, response: undefined }));
  const items = data?.items ?? [];
  const filtered = productId ?? sellerId;
  return (
    <>
      <PageHeader
        title="리뷰"
        description="최근 작성 순입니다. 숨긴 리뷰는 상품 상세와 별점에서 빠지고, 작성자와 판매자에게는 숨김으로 보입니다."
        actions={
          filtered ? (
            <Button asChild variant="outline" size="sm">
              <Link href={status ? `/reviews?status=${status}` : "/reviews"}>필터 해제</Link>
            </Button>
          ) : undefined
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      {productId && items[0]?.productName ? (
        <p className="mb-2 text-sm text-muted-foreground">상품: {items[0].productName}</p>
      ) : sellerId ? (
        <p className="mb-2 text-sm text-muted-foreground">판매자 {sellerId}의 리뷰만 봅니다.</p>
      ) : null}
      <StatusTabs
        basePath="/reviews"
        current={status}
        query={filters}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "VISIBLE", label: "보임" },
          { status: "HIDDEN", label: "숨김" },
        ]}
      />
      {!response?.ok || !data ? (
        <Alert variant="destructive">리뷰를 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>리뷰가 없습니다.</Alert>
      ) : (
        <ul aria-label="리뷰" className="grid gap-3">
          {items.map((item) => (
            <li key={item.reviewId} aria-label={`${item.productName} 리뷰`}>
              <Card>
                <CardContent className="grid gap-3 pt-6 text-sm md:grid-cols-[1fr_18rem]">
                  <div className="grid gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/reviews?productId=${item.productId}`}
                        className="font-medium hover:underline"
                      >
                        {item.productName}
                      </Link>
                      {(item.optionValues ?? []).length > 0 ? (
                        <span className="text-muted-foreground">
                          {(item.optionValues ?? []).join(" / ")}
                        </span>
                      ) : null}
                      {item.status === "HIDDEN" ? (
                        <Badge variant="secondary">숨김</Badge>
                      ) : (
                        <Badge variant="success">보임</Badge>
                      )}
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-muted-foreground">
                      <span className="text-primary" aria-label={`별점 ${item.rating}점`}>
                        {stars(item.rating ?? 0)}
                      </span>
                      <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>
                      {item.sellerId ? (
                        <Link
                          href={`/reviews?sellerId=${item.sellerId}`}
                          className="font-mono text-xs hover:underline"
                          title={item.sellerId}
                        >
                          판매자 {item.sellerId.slice(0, 8)}
                        </Link>
                      ) : null}
                    </div>
                    <p className="whitespace-pre-wrap leading-relaxed">{item.content}</p>
                    {(item.fileIds ?? []).length > 0 ? (
                      <ul aria-label="리뷰 사진" className="flex flex-wrap gap-2">
                        {(item.fileIds ?? []).map((fileId, index) => (
                          <li key={fileId}>
                            <a href={`/files/${fileId}/w800`} target="_blank" rel="noreferrer">
                              <ProductImage
                                fileId={fileId}
                                size={80}
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
                  </div>
                  <div>
                    {item.reviewId ? (
                      <details>
                        <summary
                          className={
                            item.status === "HIDDEN"
                              ? "cursor-pointer text-sm"
                              : "cursor-pointer text-sm text-destructive"
                          }
                        >
                          {item.status === "HIDDEN" ? "다시 보이기" : "숨기기"}
                        </summary>
                        <div className="pt-2">
                          <ReasonForm
                            action={item.status === "HIDDEN" ? showReview : hideReview}
                            hidden={{ reviewId: item.reviewId, returnTo }}
                            label={item.status === "HIDDEN" ? "다시 보이는 사유" : "숨기는 사유"}
                            submitText={item.status === "HIDDEN" ? "다시 보이기" : "숨기기"}
                            maxLength={200}
                            variant={item.status === "HIDDEN" ? "outline" : "destructive"}
                          />
                        </div>
                      </details>
                    ) : null}
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
      {data ? (
        <Pager
          basePath="/reviews"
          status={status}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
          query={filters}
        />
      ) : null}
    </>
  );
}
