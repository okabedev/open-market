import { unreadCount } from "@/lib/notification-count";
import { currentSession } from "@/lib/session";

/** 머리글 알림 링크가 주기적으로 읽는 안 읽은 수(BFF). 로그인하지 않았으면 0이다. */
export async function GET(): Promise<Response> {
  const session = await currentSession();
  const count = session ? await unreadCount() : 0;
  return Response.json({ count }, { headers: { "Cache-Control": "no-store" } });
}
