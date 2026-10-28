// 부하 시험이 닿는 주소와 계정. infra/compose/load.yaml이 Compose 네트워크 주소와 루트 .env 값을 넘긴다.
export const env = {
  gateway: __ENV.GATEWAY_URL || "http://gateway:8080",
  // issuer와 같은 이름이어야 로그인 세션 쿠키와 리다이렉트 주소가 맞는다(ADR 0013).
  auth: __ENV.OIDC_ISSUER || "http://auth.openmarket.local:9000",
  mailpit: __ENV.MAILPIT_URL || "http://mailpit:8025",
  adminEmail: __ENV.ADMIN_SEED_EMAIL || "admin@openmarket.local",
  adminPassword: __ENV.ADMIN_SEED_PASSWORD || "admin-local-1234",
  // 시드 관리자의 2단계 인증 비밀 값(Base32). run.sh가 admin-totp.js로 한 번 등록하고 넘긴다.
  adminTotpSecret: __ENV.ADMIN_TOTP_SECRET || "",
  clients: {
    shop: { id: "shop", secret: __ENV.SHOP_CLIENT_SECRET || "shop-local-secret", app: "http://localhost:3000" },
    seller: { id: "seller", secret: __ENV.SELLER_CLIENT_SECRET || "seller-local-secret", app: "http://localhost:3001" },
    admin: { id: "admin", secret: __ENV.ADMIN_CLIENT_SECRET || "admin-local-secret", app: "http://localhost:3002" },
  },
};

/** 실행마다 겹치지 않는 값. 같은 스택에서 다시 돌려도 이메일과 상호가 겹치지 않는다. */
export function runTag() {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}

/**
 * 가상의 클라이언트 주소. gateway는 BFF가 붙인 X-Forwarded-For의 마지막 값으로 익명 요청의 레이트 리밋 키를 정한다(RateLimitConfiguration).
 * 부하 시험은 BFF 자리에서 gateway를 부르므로, 사용자마다 다른 주소를 넣어 한 IP에 몰린 요청처럼 보이지 않게 한다.
 */
export function clientAddress(n) {
  return `10.${(n >> 16) & 255}.${(n >> 8) & 255}.${n & 255}`;
}

const WARMUP = __ENV.WARMUP_DURATION || "30s";

/**
 * 시나리오마다 예열 단계(phase:warmup)와 측정 단계(phase:measure)를 만든다. 둘 다 일정한 도착률이고 측정 단계는 예열이 끝난 뒤 시작한다.
 * 임계값은 `{phase:measure}`로 측정 단계에만 건다.
 * @param {Record<string, {exec: string, rate: number, preAllocatedVUs: number, maxVUs: number}>} specs
 */
export function phased(specs, duration) {
  const out = {};
  Object.entries(specs).forEach(([name, spec]) => {
    const base = { executor: "constant-arrival-rate", timeUnit: "1s", ...spec };
    out[`${name}_warmup`] = { ...base, duration: WARMUP, tags: { phase: "warmup" } };
    out[name] = { ...base, duration, startTime: WARMUP, tags: { phase: "measure" } };
  });
  return out;
}
