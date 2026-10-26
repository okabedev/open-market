import {
  Alert,
  Badge,
  Button,
  Card,
  cn,
  ConfirmActionForm,
  COUPON_PHASE_LABELS,
  couponPhase,
  couponTerms,
  formatDateTime,
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
import Link from "next/link";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { stopCoupon } from "./actions";

const STATUSES = ["ISSUING", "STOPPED"] as const;
const ISSUERS = ["PLATFORM", "SELLER"] as const;
type Issuer = (typeof ISSUERS)[number];

const ISSUER_LABELS: Record<Issuer, string> = { PLATFORM: "플랫폼", SELLER: "판매자" };

/**
 * 쿠폰 관리(PRM-1, ADM). 플랫폼·판매자 쿠폰 모두를 최근 생성 순, 쪽 번호로 보인다. 상태, 발행 주체, 판매자로 거른다. 플랫폼 쿠폰을 만들고,
 * 어느 쿠폰이든 발급을 멈출 수 있다. promotion은 상호를 갖지 않아 판매자 쿠폰은 판매자 ID와 그 판매자 쿠폰만 보기 링크로 보인다.
 */
export default async function CouponsPage({ searchParams }: PageProps<"/coupons">) {
  await requireSession("/coupons");
  const params = await searchParams;
  const { status, page } = listQuery(params, STATUSES, undefined);
  const issuer = ISSUERS.find((value) => value === params.issuer);
  const sellerParam = z.uuid().safeParse(params.sellerId);
  const sellerId = sellerParam.success ? sellerParam.data : undefined;
  const notice = noticeMessage(params.notice);
  const filters = { ...(issuer ? { issuer } : {}), ...(sellerId ? { sellerId } : {}) };
  const returnTo = `/coupons?${new URLSearchParams({
    ...filters,
    ...(status ? { status } : {}),
    page: String(page),
  }).toString()}`;
  const promotion = await bff.serviceClient("promotion");
  const { data, response } = await promotion
    .GET("/v1/admin/coupons", {
      params: { query: { ...filters, ...(status ? { status } : {}), page, size: PAGE_SIZE } },
    })
    .catch(() => ({ data: undefined, response: undefined }));
  const items = data?.items ?? [];
  const now = new Date();
  const issuerHref = (value: Issuer | undefined) => {
    const query = new URLSearchParams({
      ...(value ? { issuer: value } : {}),
      ...(status ? { status } : {}),
    }).toString();
    return query ? `/coupons?${query}` : "/coupons";
  };
  return (
    <>
      <PageHeader
        title="쿠폰"
        description="플랫폼 쿠폰은 모든 상품에 쓰고 할인은 플랫폼이 부담합니다. 판매자 쿠폰은 그 판매자 상품에만 씁니다."
        actions={
          <Button asChild>
            <Link href="/coupons/new">플랫폼 쿠폰 만들기</Link>
          </Button>
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <nav aria-label="발행 주체" className="mb-2 flex flex-wrap gap-2 text-sm">
        {([undefined, ...ISSUERS] as const).map((value) => {
          const active = value === issuer && !sellerId;
          return (
            <Link
              key={value ?? "ALL"}
              href={issuerHref(value)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "rounded-md border px-3 py-1",
                active
                  ? "border-primary font-medium"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {value ? ISSUER_LABELS[value] : "모든 발행 주체"}
            </Link>
          );
        })}
      </nav>
      {sellerId ? (
        <p className="mb-2 text-sm text-muted-foreground">
          판매자 {sellerId}의 쿠폰만 봅니다.{" "}
          <Link href={issuerHref(undefined)} className="underline">
            판매자 필터 해제
          </Link>
        </p>
      ) : null}
      <StatusTabs
        basePath="/coupons"
        current={status}
        query={filters}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "ISSUING", label: "발급 중" },
          { status: "STOPPED", label: "발급 중지" },
        ]}
      />
      {!response?.ok || !data ? (
        <Alert variant="destructive">쿠폰을 불러오지 못했습니다.</Alert>
      ) : items.length === 0 ? (
        <Alert>쿠폰이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>쿠폰</TableHead>
                <TableHead>발행</TableHead>
                <TableHead>단계</TableHead>
                <TableHead>발급 기간</TableHead>
                <TableHead className="text-right">발급 / 수량</TableHead>
                <TableHead>
                  <span className="sr-only">작업</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((coupon) => {
                const phase = couponPhase(coupon, now);
                return (
                  <TableRow key={coupon.couponId}>
                    <TableCell>
                      <span className="grid gap-0.5">
                        <span className="font-medium">{coupon.name}</span>
                        <span className="text-muted-foreground">{couponTerms(coupon)}</span>
                        <span className="text-xs text-muted-foreground">
                          발급 후 {coupon.validDays}일 사용
                        </span>
                      </span>
                    </TableCell>
                    <TableCell>
                      {coupon.issuer === "SELLER" && coupon.sellerId ? (
                        <Link
                          href={`/coupons?issuer=SELLER&sellerId=${coupon.sellerId}`}
                          className="grid gap-0.5 hover:underline"
                          title={coupon.sellerId}
                        >
                          <span>판매자</span>
                          <span className="font-mono text-xs text-muted-foreground">
                            {coupon.sellerId.slice(0, 8)}
                          </span>
                        </Link>
                      ) : (
                        "플랫폼"
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={phase === "ISSUING" ? "success" : "secondary"}>
                        {COUPON_PHASE_LABELS[phase]}
                      </Badge>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {formatDateTime(coupon.issueStartAt)} ~
                      <br />
                      {formatDateTime(coupon.issueEndAt)}
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap">
                      {(coupon.issuedCount ?? 0).toLocaleString("ko-KR")} /{" "}
                      {(coupon.quantity ?? 0).toLocaleString("ko-KR")}
                    </TableCell>
                    <TableCell>
                      {coupon.status === "ISSUING" && coupon.couponId ? (
                        <ConfirmActionForm
                          action={stopCoupon}
                          hidden={{ couponId: coupon.couponId, returnTo }}
                          title={`"${coupon.name}" 발급을 멈출까요?`}
                          description="다시 발급할 수 없습니다. 이미 받은 쿠폰은 사용 기한까지 쓸 수 있습니다. 감사 로그에 남습니다."
                          confirmText="발급 중지"
                          variant="outline"
                        >
                          발급 중지
                        </ConfirmActionForm>
                      ) : null}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath="/coupons"
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
