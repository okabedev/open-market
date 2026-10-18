import { redirect } from "next/navigation";
import { bff } from "./bff";

/**
 * 로그인한 사용자(역할만). 토큰을 먼저 확인해서, 만료가 가까우면 여기서 갱신하고 갱신이 거절되면 세션이 끝난 것으로 본다.
 */
export async function currentSession() {
  return (await bff.getAccessToken()) ? bff.getSession() : null;
}

/** 로그인이 필요한 화면. 로그인하지 않았으면 로그인한 뒤 `returnTo`로 돌아온다. */
export async function requireSession(returnTo: string) {
  const session = await currentSession();
  if (!session) {
    redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  }
  return session;
}
