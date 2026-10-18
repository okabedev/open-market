import {
  Alert,
  Badge,
  Card,
  formatDateTime,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import { ConfirmActionForm } from "@/components/confirm-action-form";
import { Pager } from "@/components/pager";
import { ReasonForm } from "@/components/reason-form";
import { StatusTabs } from "@/components/status-tabs";
import { bff } from "@/lib/bff";
import { listQuery, PAGE_SIZE } from "@/lib/list";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { approve, reject } from "./actions";

const STATUSES = ["SUBMITTED", "APPROVED", "REJECTED", "CANCELLED"] as const;
const LABELS: Record<(typeof STATUSES)[number], string> = {
  SUBMITTED: "심사 중",
  APPROVED: "승인",
  REJECTED: "반려",
  CANCELLED: "취소",
};

/** 입점 심사(ADM-1). 개인정보(신청자 이메일, 대표자, 예금주)는 member가 마스킹해서 준다(NFR-7). */
export default async function SellerApplicationsPage({
  searchParams,
}: PageProps<"/seller-applications">) {
  await requireSession("/seller-applications");
  const params = await searchParams;
  const { status = "SUBMITTED", page } = listQuery(params, STATUSES, "SUBMITTED");
  const notice = noticeMessage(params.notice);
  const returnTo = `/seller-applications?status=${status}&page=${page}`;
  const member = await bff.serviceClient("member");
  const { data, response } = await member.GET("/v1/admin/seller-applications", {
    params: { query: { status, page, size: PAGE_SIZE } },
  });
  return (
    <>
      <PageHeader
        title="입점 심사"
        description="오래된 신청부터 보입니다. 승인과 반려는 감사 로그에 남습니다."
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <StatusTabs
        basePath="/seller-applications"
        current={status}
        tabs={STATUSES.map((value) => ({ status: value, label: LABELS[value] }))}
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>{LABELS[status]} 상태인 신청이 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상호</TableHead>
                <TableHead>사업자 등록 번호</TableHead>
                <TableHead>신청자</TableHead>
                <TableHead>정산 계좌</TableHead>
                <TableHead>신청</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((item) => (
                <TableRow key={item.applicationId}>
                  <TableCell className="font-medium">{item.storeName}</TableCell>
                  <TableCell>{item.businessNumber}</TableCell>
                  <TableCell>
                    <div>{item.representativeName}</div>
                    <div className="text-xs text-muted-foreground">{item.applicantEmail}</div>
                  </TableCell>
                  <TableCell>
                    {item.bankName} {item.accountNumberMasked}
                    <div className="text-xs text-muted-foreground">예금주 {item.accountHolder}</div>
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.submittedAt)}
                  </TableCell>
                  <TableCell className="min-w-64">
                    {item.status === "SUBMITTED" ? (
                      <div className="grid gap-2">
                        <ConfirmActionForm
                          action={approve}
                          hidden={{ applicationId: item.applicationId ?? "", returnTo }}
                          title={`${item.storeName}을(를) 승인할까요?`}
                          description="승인하면 판매자가 되고, 다음 토큰 갱신부터 판매자 센터에 들어갈 수 있습니다."
                          confirmText="승인"
                        >
                          승인
                        </ConfirmActionForm>
                        <details>
                          <summary className="cursor-pointer text-sm text-destructive">
                            반려
                          </summary>
                          <div className="pt-2">
                            <ReasonForm
                              action={reject}
                              hidden={{ applicationId: item.applicationId ?? "", returnTo }}
                              label="반려 사유"
                              submitText="반려"
                            />
                          </div>
                        </details>
                      </div>
                    ) : (
                      <div className="grid gap-1">
                        <Badge variant={item.status === "APPROVED" ? "success" : "outline"}>
                          {LABELS[item.status ?? "CANCELLED"]}
                        </Badge>
                        <span className="text-xs text-muted-foreground">
                          {formatDateTime(item.reviewedAt)}
                        </span>
                        {item.rejectReason ? (
                          <span className="text-xs">{item.rejectReason}</span>
                        ) : null}
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      {data ? (
        <Pager
          basePath="/seller-applications"
          status={status}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
