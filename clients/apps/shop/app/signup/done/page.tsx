import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle } from "@openmarket/ui";

export default function SignupDonePage() {
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>인증 메일을 보냈습니다</CardTitle>
        <CardDescription>
          메일의 링크를 누르면 가입이 끝납니다. 링크는 24시간 동안 쓸 수 있습니다. 로컬에서는
          Mailpit(http://localhost:8025)에서 메일을 봅니다.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild variant="outline">
          <a href="/auth/login">로그인</a>
        </Button>
      </CardContent>
    </Card>
  );
}
