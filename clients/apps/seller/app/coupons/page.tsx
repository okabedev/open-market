import {
  Alert,
  Badge,
  Button,
  Card,
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
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";
import { stopCoupon } from "./actions";

const STATUSES = ["ISSUING", "STOPPED"] as const;

const NOTICES: Record<string, string> = {
  created: "쿠폰을 만들었습니다. 발급 기간이 되면 구매자가 받을 수 있습니다.",
  stopped: "발급을 멈췄습니다. 이미 받은 쿠폰은 사용 기한까지 쓸 수 있습니다.",
};

/**
 * 내 쿠폰(PRM-1). 최근에 만든 순, 쪽 번호다. 상태(발급 중, 중지)로 거르고, 발급 단계는 발급 기간으로 보인다(발급 예정, 발급 중, 발급
 * 끝남, 발급 중지). 남은 수량은 수량 - 발급 수다. 중지하지 않은 쿠폰은 발급을 멈출 수 있다.
 */
export default async function CouponsPage({ searchParams }: PageProps<"/coupons">) {
  await requireSeller("/coupons");
  const query = await searchParams;
  const { status, page } = listQuery(query, STATUSES, undefined);
  const notice = typeof query.notice === "string" ? NOTICES[query.notice] : undefined;
  const promotion = await bff.serviceClient("promotion");
  const { data, response } = await promotion
    .GET("/v1/seller/coupons", {
      params: { query: { ...(status ? { status } : {}), page, size: PAGE_SIZE } },
    })
    .catch(() => ({ data: undefined, response: undefined }));
  const now = new Date();
  return (
    <>
      <PageHeader
        title="쿠폰"
        description="내 모든 상품에 쓰는 선착순 쿠폰입니다. 쿠폰 할인은 정산 금액에서 뺍니다."
        actions={
          <Button asChild>
            <Link href="/coupons/new">쿠폰 만들기</Link>
          </Button>
        }
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <StatusTabs
        basePath="/coupons"
        current={status}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "ISSUING", label: "발급 중" },
          { status: "STOPPED", label: "발급 중지" },
        ]}
      />
      {!response?.ok || !data ? (
        <Alert variant="destructive">쿠폰을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>쿠폰이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>쿠폰</TableHead>
                <TableHead>단계</TableHead>
                <TableHead>발급 기간</TableHead>
                <TableHead className="text-right">발급 / 수량</TableHead>
                <TableHead className="text-right">사용 기한</TableHead>
                <TableHead>
                  <span className="sr-only">작업</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((coupon) => {
                const phase = couponPhase(coupon, now);
                return (
                  <TableRow key={coupon.couponId}>
                    <TableCell>
                      <span className="grid gap-0.5">
                        <span className="font-medium">{coupon.name}</span>
                        <span className="text-muted-foreground">{couponTerms(coupon)}</span>
                      </span>
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
                    <TableCell className="text-right whitespace-nowrap">
                      발급 후 {coupon.validDays}일
                    </TableCell>
                    <TableCell>
                      {coupon.status === "ISSUING" && coupon.couponId ? (
                        <ConfirmActionForm
                          action={stopCoupon}
                          hidden={{ couponId: coupon.couponId }}
                          title={`"${coupon.name}" 발급을 멈출까요?`}
                          description="다시 발급할 수 없습니다. 이미 받은 쿠폰은 사용 기한까지 쓸 수 있습니다."
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
        />
      ) : null}
    </>
  );
}
