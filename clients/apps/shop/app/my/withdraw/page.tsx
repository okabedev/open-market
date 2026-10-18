import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@openmarket/ui";
import { requireSession } from "@/lib/session";
import { WithdrawForm } from "./withdraw-form";

export default async function WithdrawPage() {
  await requireSession("/my/withdraw");
  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>탈퇴</CardTitle>
        <CardDescription>
          탈퇴하면 개인정보를 바로 지웁니다. 같은 이메일로 다시 가입할 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm">
        <ul className="list-disc pl-5 text-muted-foreground">
          <li>이름, 이메일, 연락처, 배송지가 지워집니다.</li>
          <li>심사 중인 입점 신청은 취소됩니다.</li>
          <li>판매자는 탈퇴할 수 없습니다. 고객센터로 문의해 주세요.</li>
          <li>진행 중인 주문이나 클레임이 있으면 탈퇴할 수 없습니다.</li>
        </ul>
        <WithdrawForm />
      </CardContent>
    </Card>
  );
}
