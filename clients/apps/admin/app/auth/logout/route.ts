import { bff } from "@/lib/bff";

// 같은 Origin의 폼 POST만 받는다. 세션을 지우고 auth 로그아웃으로 보낸다.
export const dynamic = "force-dynamic";

export const POST = bff.handlers.logout;
