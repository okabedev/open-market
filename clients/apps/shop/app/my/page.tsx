import {
  Alert,
  Badge,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  formatDateTime,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { ProfileForm } from "./profile-form";

export default async function MyPage() {
  await requireSession("/my");
  const member = await bff.serviceClient("member");
  const { data, response } = await member.GET("/v1/members/me");
  if (response.status === 404) {
    // 가입 이벤트가 member에 아직 오지 않았다(docs/architecture/member.md "회원 생성").
    return <Alert>회원 정보를 준비하고 있습니다. 잠시 뒤 새로고침해 주세요.</Alert>;
  }
  if (!data) {
    return <Alert variant="destructive">회원 정보를 불러오지 못했습니다.</Alert>;
  }
  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader>
          <CardTitle>내 정보</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[8rem_1fr] gap-y-3 text-sm">
            <dt className="text-muted-foreground">이메일</dt>
            <dd className="flex items-center gap-2">
              {data.email}
              {data.emailVerified ? (
                <Badge variant="success">인증됨</Badge>
              ) : (
                <Badge variant="outline">인증 전</Badge>
              )}
            </dd>
            <dt className="text-muted-foreground">가입일</dt>
            <dd>{formatDateTime(data.joinedAt)}</dd>
          </dl>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>프로필 수정</CardTitle>
        </CardHeader>
        <CardContent>
          <ProfileForm name={data.name ?? ""} phone={data.phone ?? ""} />
        </CardContent>
      </Card>
      <p className="text-sm text-muted-foreground">
        더 쓰지 않으려면{" "}
        <a href="/my/withdraw" className="underline">
          탈퇴
        </a>
        할 수 있습니다.
      </p>
    </div>
  );
}
