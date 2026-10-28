// 시드 관리자의 2단계 인증을 한 번 등록하고 비밀 값을 /out/admin-totp.json에 남긴다(run.sh가 시나리오 전에 돌린다).
// 시나리오는 ADMIN_TOTP_SECRET으로 그 값을 받아 관리자 코드를 만든다. 부하·장애 스택은 실행마다 빈 볼륨이라 처음에 한 번 등록한다.
import { adminLogin } from "./lib/auth.js";
import { env } from "./lib/env.js";

export const options = { vus: 1, iterations: 1 };

export function setup() {
  return { secret: adminLogin(env.adminTotpSecret).secret };
}

export default function () {}

export function handleSummary(data) {
  return { "/out/admin-totp.json": JSON.stringify({ secret: data.setup_data.secret }) };
}
