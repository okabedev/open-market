import { bff } from "@/lib/bff";

// auth 인가 엔드포인트로 보낸다. `?returnTo=/경로`로 로그인 뒤 돌아갈 곳을 준다.
export const dynamic = "force-dynamic";

export const GET = bff.handlers.login;
