import {
  Alert,
  Badge,
  Card,
  ConfirmActionForm,
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
import { ReasonForm } from "@/components/reason-form";
import { bff } from "@/lib/bff";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { reinstate, suspend } from "./actions";

const STATUSES = ["ACTIVE", "SUSPENDED"] as const;

/** 판매자 목록과 정지·해제(ADM-1, SEL-3). */
export default async function SellersPage({ searchParams }: PageProps<"/sellers">) {
  await requireSession("/sellers");
  const params = await searchParams;
  const { status, page } = listQuery(params, STATUSES, undefined);
  const notice = noticeMessage(params.notice);
  const returnTo = `/sellers?${status ? `status=${status}&` : ""}page=${page}`;
  const member = await bff.serviceClient("member");
  const { data, response } = await member.GET("/v1/admin/sellers", {
    params: { query: { ...(status ? { status } : {}), page, size: PAGE_SIZE } },
  });
  return (
    <>
      <PageHeader
        title="판매자"
        description="최근 승인 순입니다. 정지하면 판매자 역할이 빠지고, 상품 판매 중지는 M2부터 함께 일어납니다."
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <StatusTabs
        basePath="/sellers"
        current={status}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "ACTIVE", label: "활동 중" },
          { status: "SUSPENDED", label: "정지" },
        ]}
      />
      {!response.ok || !data ? (
        <Alert variant="destructive">목록을 불러오지 못했습니다.</Alert>
      ) : (data.items ?? []).length === 0 ? (
        <Alert>판매자가 없습니다.</Alert>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상호</TableHead>
                <TableHead>사업자 등록 번호</TableHead>
                <TableHead>대표자</TableHead>
                <TableHead>승인</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>처리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.items ?? []).map((item) => (
                <TableRow key={item.sellerId}>
                  <TableCell className="font-medium">{item.storeName}</TableCell>
                  <TableCell>{item.businessNumber}</TableCell>
                  <TableCell>{item.representativeName}</TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatDateTime(item.approvedAt)}
                  </TableCell>
                  <TableCell>
                    {item.status === "ACTIVE" ? (
                      <Badge variant="success">활동 중</Badge>
                    ) : (
                      <div className="grid gap-1">
                        <Badge variant="destructive">정지</Badge>
                        <span className="text-xs">{item.suspendReason}</span>
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="min-w-64">
                    {item.status === "ACTIVE" ? (
                      <details>
                        <summary className="cursor-pointer text-sm text-destructive">정지</summary>
                        <div className="pt-2">
                          <ReasonForm
                            action={suspend}
                            hidden={{ sellerId: item.sellerId ?? "", returnTo }}
                            label="정지 사유"
                            submitText="정지"
                          />
                        </div>
                      </details>
                    ) : (
                      <ConfirmActionForm
                        action={reinstate}
                        hidden={{ sellerId: item.sellerId ?? "", returnTo }}
                        title={`${item.storeName}의 정지를 풀까요?`}
                        description="판매자 역할이 다음 토큰 갱신부터 돌아옵니다."
                        confirmText="정지 해제"
                        variant="outline"
                      >
                        정지 해제
                      </ConfirmActionForm>
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
          basePath="/sellers"
          status={status}
          page={page}
          size={PAGE_SIZE}
          totalCount={data.totalCount ?? 0}
        />
      ) : null}
    </>
  );
}
