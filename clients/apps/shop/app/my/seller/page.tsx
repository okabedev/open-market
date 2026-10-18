import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  formatDateTime,
  PageHeader,
} from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { ApplicationForm } from "./application-form";

type StatusView = { label: string; variant: "secondary" | "success" | "destructive" | "outline" };

const CANCELLED: StatusView = { label: "취소", variant: "outline" };

const STATUS: Record<string, StatusView> = {
  SUBMITTED: { label: "심사 중", variant: "secondary" },
  APPROVED: { label: "승인", variant: "success" },
  REJECTED: { label: "반려", variant: "destructive" },
  CANCELLED,
};

const SELLER_APP_URL = process.env.SELLER_APP_URL ?? "http://localhost:3001";

export default async function SellerApplicationPage() {
  await requireSession("/my/seller");
  const member = await bff.serviceClient("member");
  const [{ data: applications = [] }, { data: profile }] = await Promise.all([
    member.GET("/v1/members/me/seller-applications"),
    member.GET("/v1/members/me"),
  ]);
  const approved = applications.some((application) => application.status === "APPROVED");
  const pending = applications.some((application) => application.status === "SUBMITTED");
  return (
    <>
      <PageHeader
        title="입점 신청"
        description="사업자 정보와 정산 계좌를 내면 관리자가 심사합니다."
      />
      <div className="grid gap-6">
        {approved ? (
          <Card>
            <CardHeader>
              <CardTitle>판매자로 승인됐습니다</CardTitle>
              <CardDescription>
                판매자 센터에서 로그인하면 상품을 관리할 수 있습니다.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <a href={SELLER_APP_URL}>판매자 센터로 가기</a>
              </Button>
            </CardContent>
          </Card>
        ) : null}
        {pending ? (
          <Alert>심사 중인 신청이 있습니다. 결과는 이 화면에서 확인할 수 있습니다.</Alert>
        ) : null}
        {!approved && !pending ? (
          profile?.emailVerified ? (
            <Card>
              <CardHeader>
                <CardTitle>새 신청</CardTitle>
              </CardHeader>
              <CardContent>
                <ApplicationForm idempotencyKey={randomUUID()} />
              </CardContent>
            </Card>
          ) : (
            <Alert>이메일 인증을 마친 뒤 신청할 수 있습니다.</Alert>
          )
        ) : null}
        {applications.length > 0 ? (
          <section className="grid gap-3">
            <h2 className="text-lg font-semibold">신청 이력</h2>
            {applications.map((application) => {
              const status = STATUS[application.status ?? ""] ?? CANCELLED;
              return (
                <Card key={application.applicationId}>
                  <CardContent className="grid gap-2 text-sm">
                    <p className="flex items-center gap-2 font-medium">
                      {application.storeName}
                      <Badge variant={status.variant}>{status.label}</Badge>
                    </p>
                    <p className="text-muted-foreground">
                      사업자 {application.businessNumber} · {application.bankName}{" "}
                      {application.accountNumberMasked}
                    </p>
                    <p className="text-muted-foreground">
                      신청 {formatDateTime(application.submittedAt)}
                      {application.reviewedAt
                        ? ` · 처리 ${formatDateTime(application.reviewedAt)}`
                        : ""}
                    </p>
                    {application.rejectReason ? (
                      <Alert variant="destructive">반려 사유: {application.rejectReason}</Alert>
                    ) : null}
                  </CardContent>
                </Card>
              );
            })}
          </section>
        ) : null}
      </div>
    </>
  );
}
