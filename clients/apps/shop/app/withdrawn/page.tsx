import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@openmarket/ui";
import { LogoutOnMount } from "./logout-on-mount";

/** 탈퇴한 뒤의 화면. 세션을 끝내기 위해 로그아웃(POST /auth/logout)한다. */
export default function WithdrawnPage() {
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>탈퇴했습니다</CardTitle>
        <CardDescription>
          그동안 이용해 주셔서 감사합니다. 잠시 뒤 처음 화면으로 돌아갑니다.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <LogoutOnMount />
      </CardContent>
    </Card>
  );
}
