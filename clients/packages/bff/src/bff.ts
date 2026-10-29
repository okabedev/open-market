import { randomBytes, randomUUID } from "node:crypto";
import * as client from "openid-client";
import type { BffConfig } from "./config";
import { loginCookieName, readCookie, serializeCookie, sessionCookieName } from "./cookies";
import { readClaims, RefreshRejectedError, type OidcClient, type TokenSet } from "./oidc";
import type { Session, SessionView } from "./session";
import type { SessionStore } from "./store";

/** 액세스 토큰이 이만큼 남으면 미리 갱신한다. gateway까지 가는 사이에 만료되지 않게 한다. */
const REFRESH_SKEW_MS = 30_000;
const LOGIN_TTL_SECONDS = 600;
/** 갱신 락 수명. auth 응답이 이보다 늦으면 다른 요청이 다시 갱신한다. */
const LOCK_TTL_MS = 10_000;
/** 다른 요청이 갱신하는 동안 기다리는 시간과 간격 */
const WAIT_TIMEOUT_MS = 5_000;
const WAIT_INTERVAL_MS = 50;

export type Bff = {
  login(request: Request): Promise<Response>;
  callback(request: Request): Promise<Response>;
  logout(request: Request): Promise<Response>;
  getSession(sid: string | undefined): Promise<SessionView | null>;
  /** 유효한 액세스 토큰. 만료가 가까우면 갱신한다. 세션이 없거나 수명이 지났거나 갱신이 거절되면 null */
  getAccessToken(sid: string | undefined): Promise<string | null>;
  /**
   * 요청의 Origin이 앱 주소와 같은지. 상태를 바꾸는 Route Handler가 부른다. Server Action과 달리 Next.js가 검사하지 않는다
   * (docs/architecture/auth.md "보안 기본값")
   */
  isSameOrigin(request: Request): boolean;
};

export type BffDependencies = {
  config: BffConfig;
  store: SessionStore;
  oidc: OidcClient;
  now?: () => number;
};

/**
 * BFF 세션과 OAuth2 로그인(docs/architecture/auth.md "로그인 흐름"). Next.js와 상관없이 표준 Request·Response만 쓴다.
 *
 * 토큰 갱신(R8): 같은 세션을 여러 요청(서버 컴포넌트, Server Action, 다른 인스턴스)이 동시에 갱신하면, 리프레시 토큰
 * rotation 때문에 늦게 쓴 쪽이 거절되고 세션이 끝난다. 그래서 Redis 락을 잡은 한 요청만 갱신하고, 나머지는 새 토큰이
 * 저장될 때까지 기다린다. 같은 프로세스 안의 동시 요청은 락 전에 Promise 하나로 묶는다.
 */
export function createBff({ config, store, oidc, now = Date.now }: BffDependencies): Bff {
  const sessionCookie = sessionCookieName(config.app);
  const loginCookie = loginCookieName(config.app);
  const inFlight = new Map<string, Promise<string | null>>();

  const redirect = (location: string, status = 302, cookies: string[] = []) => {
    const headers = new Headers({ location });
    for (const cookie of cookies) {
      headers.append("set-cookie", cookie);
    }
    return new Response(null, { status, headers });
  };
  const appUrl = (path: string) => `${config.appUrl}${path}`;
  const cookie = (name: string, value: string, maxAge: number, path = "/") =>
    serializeCookie(name, value, { maxAge, path, secure: config.secureCookies });

  const toSession = (tokens: TokenSet, createdAt: number): Session => {
    const claims = readClaims(tokens.accessToken);
    return {
      sub: claims.sub,
      roles: claims.roles,
      accessToken: tokens.accessToken,
      accessTokenExpiresAt: tokens.expiresAt,
      refreshToken: tokens.refreshToken,
      idToken: tokens.idToken,
      createdAt,
    };
  };
  const isFresh = (session: Session) => session.accessTokenExpiresAt - REFRESH_SKEW_MS > now();
  // 세션 수명은 로그인 시각부터 센다. 리프레시 토큰은 rotation마다 수명이 다시 시작되므로, 이 상한이 없으면 쓰는 동안 세션이 끝나지 않는다.
  const remainingSeconds = (session: Session) =>
    Math.ceil((session.createdAt + config.sessionTtlSeconds * 1000 - now()) / 1000);

  const isSameOrigin = (request: Request) =>
    request.headers.get("origin") === new URL(config.appUrl).origin;

  /** 수명이 남은 세션. 지났으면 지운다. */
  async function liveSession(sid: string): Promise<Session | null> {
    const session = await store.get(sid);
    if (session && remainingSeconds(session) <= 0) {
      await store.delete(sid);
      return null;
    }
    return session;
  }

  async function refresh(sid: string): Promise<string | null> {
    const owner = randomUUID();
    if (!(await store.lock(sid, owner, LOCK_TTL_MS))) {
      return waitForRefresh(sid);
    }
    try {
      // 락을 잡기 직전에 다른 요청이 갱신을 마쳤을 수 있다.
      const current = await liveSession(sid);
      if (!current) {
        return null;
      }
      if (isFresh(current)) {
        return current.accessToken;
      }
      let tokens: TokenSet;
      try {
        tokens = await oidc.refresh(current.refreshToken);
      } catch (error) {
        if (error instanceof RefreshRejectedError) {
          await store.delete(sid);
          return null;
        }
        throw error;
      }
      const next = toSession(tokens, current.createdAt);
      await store.save(sid, next, Math.max(1, remainingSeconds(next)));
      return next.accessToken;
    } finally {
      await store.unlock(sid, owner);
    }
  }

  async function waitForRefresh(sid: string): Promise<string | null> {
    const started = Date.now();
    while (Date.now() - started < WAIT_TIMEOUT_MS) {
      await new Promise((resolve) => setTimeout(resolve, WAIT_INTERVAL_MS));
      const session = await liveSession(sid);
      if (!session) {
        return null;
      }
      if (isFresh(session)) {
        return session.accessToken;
      }
    }
    throw new Error("다른 요청의 토큰 갱신을 기다리다 시간이 지났다");
  }

  return {
    async login(request) {
      const returnTo = new URL(request.url).searchParams.get("returnTo") ?? "/";
      const state = client.randomState();
      const nonce = client.randomNonce();
      const codeVerifier = client.randomPKCECodeVerifier();
      await store.saveLogin(
        state,
        { codeVerifier, nonce, returnTo: safeReturnTo(returnTo) },
        LOGIN_TTL_SECONDS,
      );
      const url = await oidc.authorizationUrl({
        state,
        nonce,
        codeChallenge: await client.calculatePKCECodeChallenge(codeVerifier),
      });
      // state를 이 브라우저에 묶는다. 다른 사람이 만든 콜백 URL로 로그인시키는 공격(login CSRF)을 막는다.
      return redirect(url.toString(), 302, [
        cookie(loginCookie, state, LOGIN_TTL_SECONDS, "/auth"),
      ]);
    },

    async callback(request) {
      const url = new URL(request.url);
      const state = url.searchParams.get("state");
      const clearLogin = cookie(loginCookie, "", 0, "/auth");
      if (!state || readCookie(request, loginCookie) !== state) {
        return new Response("로그인 요청이 올바르지 않습니다. 다시 로그인해 주세요.", {
          status: 400,
        });
      }
      const login = await store.takeLogin(state);
      if (!login) {
        return new Response("로그인 요청이 만료됐습니다. 다시 로그인해 주세요.", { status: 400 });
      }
      const error = url.searchParams.get("error");
      if (error) {
        return redirect(appUrl(`/?login_error=${encodeURIComponent(error)}`), 302, [clearLogin]);
      }
      // 프록시 뒤에서도 auth에 등록한 redirect_uri와 같은 주소로 맞춘다.
      const currentUrl = new URL(`/auth/callback${url.search}`, config.appUrl);
      let tokens: TokenSet;
      try {
        tokens = await oidc.exchangeCode(currentUrl, {
          state,
          nonce: login.nonce,
          codeVerifier: login.codeVerifier,
        });
      } catch (e) {
        if (e instanceof RefreshRejectedError) {
          return redirect(appUrl("/?login_error=invalid_grant"), 302, [clearLogin]);
        }
        throw e;
      }
      // 세션 고정 공격을 막기 위해 로그인할 때마다 새 세션 ID를 만들고, 있던 세션은 지운다.
      const previous = readCookie(request, sessionCookie);
      if (previous) {
        await store.delete(previous);
      }
      const sid = randomBytes(32).toString("base64url");
      await store.save(sid, toSession(tokens, now()), config.sessionTtlSeconds);
      return redirect(appUrl(login.returnTo), 302, [
        clearLogin,
        cookie(sessionCookie, sid, config.sessionTtlSeconds),
      ]);
    },

    isSameOrigin,

    async logout(request) {
      if (!isSameOrigin(request)) {
        return new Response("허용하지 않는 요청입니다.", { status: 403 });
      }
      const clear = cookie(sessionCookie, "", 0);
      const sid = readCookie(request, sessionCookie);
      const session = sid ? await store.get(sid) : null;
      if (!sid || !session) {
        return redirect(appUrl("/"), 303, [clear]);
      }
      await store.delete(sid);
      try {
        await oidc.revoke(session.refreshToken);
      } catch {
        // 폐기에 실패해도 로그아웃은 진행한다. 세션이 지워졌으므로 리프레시 토큰은 다시 쓰이지 않는다.
      }
      const endSession = await oidc.endSessionUrl(session.idToken, appUrl("/"));
      return redirect(endSession.toString(), 303, [clear]);
    },

    async getSession(sid) {
      if (!sid) {
        return null;
      }
      const session = await liveSession(sid);
      return session ? { sub: session.sub, roles: session.roles } : null;
    },

    async getAccessToken(sid) {
      if (!sid) {
        return null;
      }
      const session = await liveSession(sid);
      if (!session) {
        return null;
      }
      if (isFresh(session)) {
        return session.accessToken;
      }
      let pending = inFlight.get(sid);
      if (!pending) {
        pending = refresh(sid).finally(() => inFlight.delete(sid));
        inFlight.set(sid, pending);
      }
      return pending;
    },
  };
}

/** 앱 안의 경로만 허용한다. `//host`, `/\host`, 절대 URL은 열린 리다이렉트가 되므로 `/`로 바꾼다. */
export function safeReturnTo(value: string): string {
  if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return "/";
  }
  return value;
}
