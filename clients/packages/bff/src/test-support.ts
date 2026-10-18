import { RedisContainer, type StartedRedisContainer } from "@testcontainers/redis";
import { createClient, type RedisClientType } from "redis";
import type { OidcClient, TokenSet } from "./oidc";
import { RefreshRejectedError } from "./oidc";

/** compose.yaml의 Redis와 같은 이미지. */
export const REDIS_IMAGE = "redis:8.10.2-alpine";

export async function startRedis(): Promise<{
  container: StartedRedisContainer;
  client: RedisClientType;
}> {
  const container = await new RedisContainer(REDIS_IMAGE).start();
  const client = createClient({ url: container.getConnectionUrl() });
  await client.connect();
  return { container, client };
}

/** 페이로드만 보고 쓰는 테스트용 JWT. 서명은 검증하지 않는다(BFF도 토큰 응답의 클레임을 읽기만 한다). */
export function fakeJwt(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none" })}.${encode(claims)}.sig`;
}

/**
 * auth를 흉내 낸 OIDC 클라이언트. 리프레시 토큰은 한 번만 쓸 수 있다(rotation). 이미 쓴 토큰으로 갱신하면 거절한다.
 * 갱신 응답은 지연을 줄 수 있어 동시 갱신 경쟁을 만든다.
 */
export class FakeOidc implements OidcClient {
  refreshCalls = 0;
  revoked: string[] = [];
  refreshDelayMs = 0;
  roles = ["BUYER"];
  private validRefreshTokens = new Set<string>();
  private counter = 0;

  constructor(private readonly now: () => number) {}

  async authorizationUrl(params: { state: string; nonce: string; codeChallenge: string }) {
    const url = new URL("http://auth.test/oauth2/authorize");
    url.searchParams.set("state", params.state);
    url.searchParams.set("nonce", params.nonce);
    url.searchParams.set("code_challenge", params.codeChallenge);
    url.searchParams.set("code_challenge_method", "S256");
    return url;
  }

  async exchangeCode(
    currentUrl: URL,
    checks: { state: string; nonce: string; codeVerifier: string },
  ) {
    if (currentUrl.searchParams.get("code") !== "good-code") {
      throw new RefreshRejectedError("invalid_grant");
    }
    if (!checks.codeVerifier) {
      throw new Error("verifier 없음");
    }
    return this.issue();
  }

  async refresh(refreshToken: string): Promise<TokenSet> {
    this.refreshCalls += 1;
    if (this.refreshDelayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, this.refreshDelayMs));
    }
    if (!this.validRefreshTokens.delete(refreshToken)) {
      throw new RefreshRejectedError("invalid_grant");
    }
    return this.issue();
  }

  async revoke(refreshToken: string): Promise<void> {
    this.revoked.push(refreshToken);
    this.validRefreshTokens.delete(refreshToken);
  }

  async endSessionUrl(idToken: string, postLogoutRedirectUri: string): Promise<URL> {
    const url = new URL("http://auth.test/connect/logout");
    url.searchParams.set("id_token_hint", idToken);
    url.searchParams.set("post_logout_redirect_uri", postLogoutRedirectUri);
    return url;
  }

  /** 지금 유효한 리프레시 토큰을 만료시켜 다른 곳에서 폐기된 상황을 만든다. */
  revokeAll(): void {
    this.validRefreshTokens.clear();
  }

  private issue(): TokenSet {
    this.counter += 1;
    const refreshToken = `refresh-${this.counter}`;
    this.validRefreshTokens.add(refreshToken);
    return {
      accessToken: fakeJwt({ sub: "account-1", roles: this.roles, n: this.counter }),
      refreshToken,
      idToken: fakeJwt({ sub: "account-1" }),
      expiresAt: this.now() + 600_000,
    };
  }
}
