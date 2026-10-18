/** 앱 이름. OAuth2 클라이언트 ID와 같다(docs/architecture/overview.md). */
export type AppName = "shop" | "seller" | "admin";

export type BffConfig = {
  app: AppName;
  /** 브라우저가 앱에 닿는 주소. 콜백·로그아웃 리다이렉트 주소와 Origin 검사에 쓴다 */
  appUrl: string;
  /** 세션 수명. auth의 리프레시 토큰 수명과 같게 둔다(docs/architecture/auth.md "토큰") */
  sessionTtlSeconds: number;
  /** 쿠키 Secure 속성. 로컬 HTTP에서는 끈다 */
  secureCookies: boolean;
};

export type OidcConfig = {
  issuer: string;
  clientId: AppName;
  clientSecret: string;
  redirectUri: string;
};

const APP_PORTS: Record<AppName, number> = { shop: 3000, seller: 3001, admin: 3002 };

const DAY = 24 * 60 * 60;
const SESSION_TTL_SECONDS: Record<AppName, number> = {
  shop: 14 * DAY,
  seller: 14 * DAY,
  admin: 8 * 60 * 60,
};

const DEFAULT_ISSUER = "http://auth.openmarket.local:9000";

type Env = Record<string, string | undefined>;

/**
 * 환경 변수에서 설정을 읽는다. 앱은 레포 루트 `.env`를 읽는다(next.config.ts).
 * - `<APP>_CLIENT_SECRET`(필수), `OIDC_ISSUER`, `APP_URL`, `REDIS_URL`, `SESSION_COOKIE_SECURE`
 */
export function loadConfig(app: AppName, env: Env = process.env) {
  const secretName = `${app.toUpperCase()}_CLIENT_SECRET`;
  const clientSecret = env[secretName];
  if (!clientSecret) {
    throw new Error(`${secretName} 환경 변수가 없다. 레포 루트 .env를 확인한다(docs/dev-setup.md)`);
  }
  const appUrl = (env.APP_URL ?? `http://localhost:${APP_PORTS[app]}`).replace(/\/+$/, "");
  const bff: BffConfig = {
    app,
    appUrl,
    sessionTtlSeconds: SESSION_TTL_SECONDS[app],
    secureCookies: env.SESSION_COOKIE_SECURE === "true",
  };
  const oidc: OidcConfig = {
    issuer: env.OIDC_ISSUER ?? DEFAULT_ISSUER,
    clientId: app,
    clientSecret,
    redirectUri: `${appUrl}/auth/callback`,
  };
  return { bff, oidc, redisUrl: env.REDIS_URL ?? "redis://localhost:6379" };
}
