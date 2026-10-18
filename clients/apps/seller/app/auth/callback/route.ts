import { bff } from "@/lib/bff";

// auth가 인가 코드를 들고 돌아오는 곳. auth에 등록한 redirect_uri와 같다(infra/config-repo/auth.yml).
export const dynamic = "force-dynamic";

export const GET = bff.handlers.callback;
