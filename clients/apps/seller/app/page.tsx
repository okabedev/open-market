import { AccountBar, Placeholder } from "@openmarket/ui";
import { bff } from "@/lib/bff";

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function HomePage({ searchParams }: PageProps<"/">) {
  const { login_error: loginError } = await searchParams;
  // 토큰을 먼저 확인한다. 만료가 가까우면 여기서 갱신하고, 갱신이 거절되면 세션이 끝난 것으로 본다.
  const session = (await bff.getAccessToken()) ? await bff.getSession() : null;
  return (
    <>
      <AccountBar
        roles={session?.roles ?? null}
        loginError={typeof loginError === "string" ? loginError : undefined}
      />
      <Placeholder
        title="오픈 마켓 판매자 센터"
        description="입점 신청과 상품 관리 화면은 M1부터 만든다."
      />
    </>
  );
}
