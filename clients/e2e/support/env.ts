/** E2E가 닿는 주소와 계정. 기본값은 로컬 Compose와 `.env.example`이다. `run.sh`가 레포 루트 `.env`를 환경 변수로 넘긴다. */
export const env = {
  shop: process.env.SHOP_APP_URL ?? "http://localhost:3000",
  seller: process.env.SELLER_APP_URL ?? "http://localhost:3001",
  admin: process.env.ADMIN_APP_URL ?? "http://localhost:3002",
  gateway: process.env.GATEWAY_URL ?? "http://localhost:8080",
  auth: process.env.OIDC_ISSUER ?? "http://auth.openmarket.local:9000",
  mailpit: process.env.MAILPIT_URL ?? "http://localhost:8025",
  /** 가짜 PG(infra/fake-pg). 토스 대신 결제 인증 화면과 제어 API를 준다 */
  fakePg: process.env.FAKE_PG_URL ?? "http://localhost:8090",
  adminEmail: process.env.ADMIN_SEED_EMAIL ?? "admin@openmarket.local",
  adminPassword: process.env.ADMIN_SEED_PASSWORD ?? "admin-local-1234",
  sellerClientSecret: process.env.SELLER_CLIENT_SECRET ?? "seller-local-secret",
};

/** 실행마다 겹치지 않는 값. 같은 스택에서 다시 돌려도 이메일과 상호가 겹치지 않는다. */
export function uniqueTag(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}
