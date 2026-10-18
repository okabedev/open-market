import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { ResendForm } from "./resend-form";

/**
 * 인증 메일의 링크(`/verify-email?token=`). 화면을 열면 바로 인증한다. auth는 같은 토큰을 다시 보내도 204라서 새로고침해도 결과가 같다.
 */
export default async function VerifyEmailPage({ searchParams }: PageProps<"/verify-email">) {
  const { token } = await searchParams;
  const verified = typeof token === "string" && token.length > 0 ? await verify(token) : false;
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>{verified ? "이메일을 인증했습니다" : "인증하지 못했습니다"}</CardTitle>
        <CardDescription>
          {verified
            ? "이제 로그인해서 쓸 수 있습니다."
            : "링크가 만료됐거나 잘못됐습니다. 인증 메일을 다시 받아 주세요."}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {verified ? (
          <Button asChild>
            <a href="/auth/login?returnTo=/my">로그인</a>
          </Button>
        ) : (
          <>
            <Alert variant="destructive">인증 링크는 24시간 동안 한 번 쓸 수 있습니다.</Alert>
            <ResendForm />
          </>
        )}
      </CardContent>
    </Card>
  );
}

async function verify(token: string): Promise<boolean> {
  const auth = await bff.publicClient("auth");
  const { response } = await auth.POST("/v1/accounts/email-verification", { body: { token } });
  return response.ok;
}
