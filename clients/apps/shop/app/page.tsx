import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  loginErrorMessage,
} from "@openmarket/ui";
import { currentSession } from "@/lib/session";

export default async function HomePage({ searchParams }: PageProps<"/">) {
  const { login_error: loginError } = await searchParams;
  const error = loginErrorMessage(typeof loginError === "string" ? loginError : undefined);
  const session = await currentSession();
  return (
    <div className="grid gap-6">
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      <section className="grid gap-2 py-6">
        <h1 className="text-3xl font-bold tracking-tight">여러 판매자의 상품을 한곳에서</h1>
        <p className="text-muted-foreground">상품 둘러보기와 주문은 다음 마일스톤에서 열립니다.</p>
      </section>
      {session ? (
        <Card>
          <CardHeader>
            <CardTitle>내 정보</CardTitle>
            <CardDescription>
              프로필과 배송지를 관리하고, 판매자로 입점을 신청할 수 있습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button asChild>
              <a href="/my">내 정보</a>
            </Button>
            <Button asChild variant="outline">
              <a href="/my/seller">입점 신청</a>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>처음 오셨나요?</CardTitle>
            <CardDescription>
              이메일로 가입하고 인증 메일의 링크를 누르면 바로 시작할 수 있습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button asChild>
              <a href="/signup">가입하기</a>
            </Button>
            <Button asChild variant="outline">
              <a href="/auth/login">로그인</a>
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
