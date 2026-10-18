import * as client from "openid-client";
import type { OidcConfig } from "./config";

export type TokenSet = {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  /** epoch ms */
  expiresAt: number;
};

/** auth가 리프레시 토큰이나 인가 코드를 거절했다(invalid_grant). 세션을 끝내야 한다. */
export class RefreshRejectedError extends Error {
  constructor(readonly code: string) {
    super(`auth가 토큰 요청을 거절했다: ${code}`);
    this.name = "RefreshRejectedError";
  }
}

/** BFF가 auth에 하는 일. 구현은 openid-client이고, 테스트는 가짜를 쓴다. */
export interface OidcClient {
  authorizationUrl(params: { state: string; nonce: string; codeChallenge: string }): Promise<URL>;
  exchangeCode(
    currentUrl: URL,
    checks: { state: string; nonce: string; codeVerifier: string },
  ): Promise<TokenSet>;
  refresh(refreshToken: string): Promise<TokenSet>;
  revoke(refreshToken: string): Promise<void>;
  endSessionUrl(idToken: string, postLogoutRedirectUri: string): Promise<URL>;
}

const SCOPE = "openid profile api";

/**
 * openid-client 구현. discovery 문서는 처음 쓸 때 받고 캐시한다. 실패하면 다음 호출에서 다시 받는다.
 * 로컬 issuer가 HTTP라서 allowInsecureRequests를 켠다. issuer가 HTTPS면 끈다.
 */
export function createOidcClient(config: OidcConfig): OidcClient {
  let discovered: Promise<client.Configuration> | undefined;
  const configuration = () => {
    discovered ??= client
      .discovery(
        new URL(config.issuer),
        config.clientId,
        undefined,
        client.ClientSecretBasic(config.clientSecret),
        new URL(config.issuer).protocol === "http:"
          ? { execute: [client.allowInsecureRequests] }
          : undefined,
      )
      .catch((error: unknown) => {
        discovered = undefined;
        throw error;
      });
    return discovered;
  };

  return {
    async authorizationUrl({ state, nonce, codeChallenge }) {
      return client.buildAuthorizationUrl(await configuration(), {
        redirect_uri: config.redirectUri,
        scope: SCOPE,
        state,
        nonce,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
      });
    },

    async exchangeCode(currentUrl, { state, nonce, codeVerifier }) {
      const tokens = await rejectInvalidGrant(async () =>
        client.authorizationCodeGrant(await configuration(), currentUrl, {
          pkceCodeVerifier: codeVerifier,
          expectedState: state,
          expectedNonce: nonce,
          idTokenExpected: true,
        }),
      );
      return toTokenSet(tokens);
    },

    async refresh(refreshToken) {
      const tokens = await rejectInvalidGrant(async () =>
        client.refreshTokenGrant(await configuration(), refreshToken),
      );
      return toTokenSet(tokens);
    },

    async revoke(refreshToken) {
      await client.tokenRevocation(await configuration(), refreshToken, {
        token_type_hint: "refresh_token",
      });
    },

    async endSessionUrl(idToken, postLogoutRedirectUri) {
      return client.buildEndSessionUrl(await configuration(), {
        id_token_hint: idToken,
        post_logout_redirect_uri: postLogoutRedirectUri,
      });
    },
  };
}

async function rejectInvalidGrant<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call();
  } catch (error) {
    if (error instanceof client.ResponseBodyError && error.error === "invalid_grant") {
      throw new RefreshRejectedError(error.error);
    }
    throw error;
  }
}

function toTokenSet(tokens: Awaited<ReturnType<typeof client.refreshTokenGrant>>): TokenSet {
  if (!tokens.refresh_token || !tokens.id_token) {
    throw new Error("토큰 응답에 refresh_token 또는 id_token이 없다");
  }
  return {
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    idToken: tokens.id_token,
    expiresAt: Date.now() + (tokens.expiresIn() ?? 0) * 1000,
  };
}

/** 액세스 토큰 페이로드를 읽는다. 토큰은 auth에서 백 채널로 직접 받았으므로 서명을 다시 검증하지 않는다. */
export function readClaims(jwt: string): { sub: string; roles: string[] } {
  const payload = jwt.split(".")[1];
  if (!payload) {
    throw new Error("JWT 형식이 아니다");
  }
  const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as {
    sub?: unknown;
    roles?: unknown;
  };
  if (typeof claims.sub !== "string") {
    throw new Error("토큰에 sub가 없다");
  }
  const roles = Array.isArray(claims.roles)
    ? claims.roles.filter((r): r is string => typeof r === "string")
    : [];
  return { sub: claims.sub, roles };
}
