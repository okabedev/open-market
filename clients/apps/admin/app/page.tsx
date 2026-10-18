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
import { redirect } from "next/navigation";
import { currentSession } from "@/lib/session";

export default async function HomePage({ searchParams }: PageProps<"/">) {
  const { login_error: loginError } = await searchParams;
  if (await currentSession()) {
    redirect("/seller-applications");
  }
  const error = loginErrorMessage(typeof loginError === "string" ? loginError : undefined);
  return (
    <div className="mx-auto grid max-w-md gap-4">
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      <Card>
        <CardHeader>
          <CardTitle>관리자 로그인</CardTitle>
          <CardDescription>관리자 역할이 있는 계정만 들어올 수 있습니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild>
            <a href="/auth/login?returnTo=/seller-applications">로그인</a>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
