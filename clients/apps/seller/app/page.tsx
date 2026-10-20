import Link from "next/link";
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
  loginErrorMessage,
  PageHeader,
} from "@openmarket/ui";
import { isSeller } from "@/lib/access";
import { bff } from "@/lib/bff";
import { currentSession } from "@/lib/session";

const SHOP_APP_URL = process.env.SHOP_APP_URL ?? "http://localhost:3000";

export default async function HomePage({ searchParams }: PageProps<"/">) {
  const { login_error: loginError } = await searchParams;
  const error = loginErrorMessage(typeof loginError === "string" ? loginError : undefined);
  const session = await currentSession();
  if (!session) {
    return (
      <div className="mx-auto grid max-w-md gap-4">
        {error ? <Alert variant="destructive">{error}</Alert> : null}
        <Card>
          <CardHeader>
            <CardTitle>판매자 센터</CardTitle>
            <CardDescription>입점 승인을 받은 판매자 계정으로 로그인합니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild>
              <a href="/auth/login">로그인</a>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }
  if (!isSeller(session.roles)) {
    return <NotSeller />;
  }
  const member = await bff.serviceClient("member");
  const { data: seller, response } = await member.GET("/v1/seller/me");
  if (response.status === 403 || response.status === 404) {
    // 정지됐거나 승인이 취소됐는데 토큰에 아직 역할이 남아 있다.
    return <NotSeller />;
  }
  if (!seller) {
    return <Alert variant="destructive">판매자 정보를 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      <PageHeader
        title={seller.storeName ?? "판매자"}
        description="상품을 등록하고 판매 상태와 재고를 관리합니다. 주문 관리는 다음 마일스톤에서 열립니다."
        actions={
          <Button asChild>
            <Link href="/products">상품 관리</Link>
          </Button>
        }
      />
      {seller.status === "SUSPENDED" ? (
        <Alert variant="destructive" className="mb-6">
          판매가 정지됐습니다. 사유: {seller.suspendReason}
        </Alert>
      ) : null}
      <Card>
        <CardHeader>
          <CardTitle>판매자 정보</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[8rem_1fr] gap-y-3 text-sm">
            <dt className="text-muted-foreground">상태</dt>
            <dd>
              {seller.status === "ACTIVE" ? (
                <Badge variant="success">활동 중</Badge>
              ) : (
                <Badge variant="destructive">정지</Badge>
              )}
            </dd>
            <dt className="text-muted-foreground">사업자 등록 번호</dt>
            <dd>{seller.businessNumber}</dd>
            <dt className="text-muted-foreground">대표자</dt>
            <dd>{seller.representativeName}</dd>
            <dt className="text-muted-foreground">정산 계좌</dt>
            <dd>
              {seller.bankName} {seller.accountNumberMasked} ({seller.accountHolder})
            </dd>
            <dt className="text-muted-foreground">승인일</dt>
            <dd>{formatDateTime(seller.approvedAt)}</dd>
          </dl>
        </CardContent>
      </Card>
    </>
  );
}

/** 판매자 역할이 없는 계정(403). 승인 직후라면 다시 로그인하면 새 역할이 담긴 토큰을 받는다. */
function NotSeller() {
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>판매자 권한이 없습니다</CardTitle>
        <CardDescription>
          판매자 센터는 입점 승인을 받은 계정만 쓸 수 있습니다. 방금 승인됐다면 권한을 다시 확인해
          주세요.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        <Button asChild>
          <a href={`${SHOP_APP_URL}/my/seller`}>입점 신청하기</a>
        </Button>
        <Button asChild variant="outline">
          <a href="/auth/login">권한 다시 확인</a>
        </Button>
      </CardContent>
    </Card>
  );
}
