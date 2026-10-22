import { RedisContainer, type StartedRedisContainer } from "@testcontainers/redis";
import { createClient, type RedisClientType } from "redis";
import type { OidcClient, TokenSet } from "./oidc";
import { RefreshRejectedError } from "./oidc";

/** compose.yaml의 Redis와 같은 이미지. */
export const REDIS_IMAGE = "redis:8.10.2-alpine";

const START_ATTEMPTS = 3;

/**
 * 테스트용 Redis 컨테이너와 클라이언트. 가끔 매핑된 포트에서 Redis가 아닌 서버가 답해 "Unknown RESP type 72"(HTTP 응답의 `H`)로
 * 실패했다(M2~M4, 재현하지 못했다). 그래서
 * - 클라이언트는 다시 연결하지 않는다. 컨테이너가 멈춘 뒤 재사용된 포트에 붙지 않는다.
 * - 연결한 뒤 `PING`으로 Redis인지 확인하고, 아니면 새 컨테이너로 다시 띄운다. 끝내 안 되면 포트를 적어 실패한다.
 */
export async function startRedis(): Promise<{
  container: StartedRedisContainer;
  client: RedisClientType;
}> {
  const failures: string[] = [];
  for (let attempt = 1; attempt <= START_ATTEMPTS; attempt += 1) {
    const container = await new RedisContainer(REDIS_IMAGE).start();
    const url = container.getConnectionUrl();
    const client: RedisClientType = createClient({ url, socket: { reconnectStrategy: false } });
    client.on("error", () => {
      // 연결 오류는 connect·명령의 거절로 드러난다. 리스너가 없으면 프로세스 오류가 된다.
    });
    try {
      await client.connect();
      if ((await client.ping()) === "PONG") {
        return { container, client };
      }
      failures.push(`${url}: PING 응답이 PONG이 아니다`);
    } catch (error) {
      failures.push(`${url}: ${error instanceof Error ? error.message : String(error)}`);
    }
    client.destroy();
    await container.stop();
  }
  throw new Error(`Redis 컨테이너에 연결하지 못했다(${START_ATTEMPTS}번):\n${failures.join("\n")}`);
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
