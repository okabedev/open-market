import {
  Alert,
  Card,
  ConfirmActionForm,
  formatDateTime,
  formatPrice,
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
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { retryCancel, retryRefund } from "./actions";

const KINDS = ["REFUND", "CANCEL"] as const;

/**
 * 결제 실패 재시도(PAY-2, docs/architecture/payment.md "관리자 재시도"). 토스가 거절한 클레임 환불(`RefundFailed`)과 결제 기한이 지난
 * 주문의 보상 취소를 오래된 순으로 보여 주고 다시 시도한다. 재시도하면 목록에서 빠지고, 다시 거절되면 돌아온다.
 */
export default async function PaymentFailuresPage({
  searchParams,
}: PageProps<"/payment-failures">) {
  await requireSession("/payment-failures");
  const params = await searchParams;
  const { status: kind = "REFUND", page } = listQuery(params, KINDS, "REFUND");
  const notice = noticeMessage(params.notice);
  const returnTo = `/payment-failures?status=${kind}&page=${page}`;
  const payment = await bff.serviceClient("payment");
  return (
    <>
      <PageHeader
        title="결제 실패"
        description="토스가 거절한 환불과 결제 취소입니다. 원인(실패 코드)을 확인한 뒤 다시 시도합니다."
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <StatusTabs
        basePath="/payment-failures"
        current={kind}
        tabs={[
          { status: "REFUND", label: "환불 실패" },
          { status: "CANCEL", label: "결제 취소 거절" },
        ]}
      />
      {kind === "REFUND" ? (
        <Refunds payment={payment} page={page} returnTo={returnTo} />
      ) : (
        <Cancels payment={payment} page={page} returnTo={returnTo} />
      )}
    </>
  );
}

type PaymentClient = Awaited<ReturnType<typeof bff.serviceClient<"payment">>>;

async function Refunds({
  payment,
  page,
  returnTo,
}: {
  payment: PaymentClient;
  page: number;
  returnTo: string;
}) {
  const { data, response } = await payment.GET("/v1/admin/refunds/failed", {
    params: { query: { page, size: PAGE_SIZE } },
  });
  const items = data?.items ?? [];
  if (!response.ok || !data) {
    return <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      {items.length === 0 ? (
        <Alert>실패한 환불이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>실패</TableHead>
                <TableHead>주문</TableHead>
                <TableHead className="text-right">금액</TableHead>
                <TableHead>실패 코드</TableHead>
                <TableHead className="text-right">시도</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.refundId}>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.failedAt)}
                  </TableCell>
                  <TableCell className="font-mono text-xs break-all">{item.orderId}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.amount)}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{item.failureCode}</TableCell>
                  <TableCell className="text-right">{item.attempt}</TableCell>
                  <TableCell>
                    <ConfirmActionForm
                      action={retryRefund}
                      hidden={{ refundId: item.refundId ?? "", returnTo }}
                      title="환불을 다시 시도할까요?"
                      description={`${formatPrice(item.amount)}을 새 요청으로 토스에 다시 보냅니다. 원인이 해결됐는지 먼저 확인해 주세요.`}
                      confirmText="다시 시도"
                    >
                      다시 시도
                    </ConfirmActionForm>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <Pager
        basePath="/payment-failures"
        status="REFUND"
        page={page}
        size={PAGE_SIZE}
        totalCount={data.totalCount ?? 0}
      />
    </>
  );
}

async function Cancels({
  payment,
  page,
  returnTo,
}: {
  payment: PaymentClient;
  page: number;
  returnTo: string;
}) {
  const { data, response } = await payment.GET("/v1/admin/payments/cancel-failures", {
    params: { query: { page, size: PAGE_SIZE } },
  });
  const items = data?.items ?? [];
  if (!response.ok || !data) {
    return <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      {items.length === 0 ? (
        <Alert>거절된 결제 취소가 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>취소 요청</TableHead>
                <TableHead>주문</TableHead>
                <TableHead className="text-right">금액</TableHead>
                <TableHead>실패 코드</TableHead>
                <TableHead className="text-right">시도</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.paymentId}>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.cancelRequestedAt)}
                  </TableCell>
                  <TableCell className="font-mono text-xs break-all">{item.orderId}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(item.amount)}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{item.cancelFailureCode}</TableCell>
                  <TableCell className="text-right">{item.cancelAttempt}</TableCell>
                  <TableCell>
                    <ConfirmActionForm
                      action={retryCancel}
                      hidden={{ paymentId: item.paymentId ?? "", returnTo }}
                      title="결제 취소를 다시 시도할까요?"
                      description={`${formatPrice(item.amount)} 전액 취소를 새 요청으로 토스에 다시 보냅니다.`}
                      confirmText="다시 시도"
                    >
                      다시 시도
                    </ConfirmActionForm>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <Pager
        basePath="/payment-failures"
        status="CANCEL"
        page={page}
        size={PAGE_SIZE}
        totalCount={data.totalCount ?? 0}
      />
    </>
  );
}
