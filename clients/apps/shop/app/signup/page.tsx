import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { currentSession } from "@/lib/session";
import { SignupForm } from "./signup-form";

export default async function SignupPage() {
  if (await currentSession()) {
    redirect("/my");
  }
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>가입하기</CardTitle>
        <CardDescription>
          가입하면 인증 메일을 보냅니다. 이미 계정이 있으면{" "}
          <a href="/auth/login" className="text-primary underline">
            로그인
          </a>
          하세요.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <SignupForm />
      </CardContent>
    </Card>
  );
}
