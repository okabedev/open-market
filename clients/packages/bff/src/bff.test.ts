import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { createBff, type Bff } from "./bff";
import type { BffConfig } from "./config";
import { RedisSessionStore } from "./store";
import { FakeOidc, startRedis } from "./test-support";

let redis: Awaited<ReturnType<typeof startRedis>>;
let clock = 1_000_000;
const now = () => clock;

const config: BffConfig = {
  app: "shop",
  appUrl: "http://localhost:3000",
  sessionTtlSeconds: 3600,
  secureCookies: false,
};

beforeAll(async () => {
  redis = await startRedis();
});

afterAll(async () => {
  await redis.client.close();
  await redis.container.stop();
});

function newBff(oidc: FakeOidc): Bff {
  return createBff({ config, store: new RedisSessionStore(redis.client, "shop"), oidc, now });
}

function cookieValue(response: Response, name: string): string | undefined {
  const header = response.headers.getSetCookie().find((c) => c.startsWith(`${name}=`));
  return header?.split(";")[0]?.slice(name.length + 1);
}

/** 로그인 → 콜백까지 마치고 세션 ID를 돌려준다. */
async function login(bff: Bff, returnTo = "/orders"): Promise<string> {
  const start = await bff.login(
    new Request(`http://localhost:3000/auth/login?returnTo=${encodeURIComponent(returnTo)}`),
  );
  const state = new URL(start.headers.get("location")!).searchParams.get("state")!;
  const loginCookie = cookieValue(start, "om_login_shop")!;
  const callback = await bff.callback(
    new Request(`http://localhost:3000/auth/callback?code=good-code&state=${state}`, {
      headers: { cookie: `om_login_shop=${loginCookie}` },
    }),
  );
  return cookieValue(callback, "om_sid_shop")!;
}

describe("로그인", () => {
  let oidc: FakeOidc;
  let bff: Bff;

  beforeEach(() => {
    oidc = new FakeOidc(now);
    bff = newBff(oidc);
  });

  it("인가 엔드포인트로 보내고 state를 HttpOnly 쿠키에 묶는다", async () => {
    const response = await bff.login(new Request("http://localhost:3000/auth/login"));

    expect(response.status).toBe(302);
    const location = new URL(response.headers.get("location")!);
    expect(location.searchParams.get("code_challenge_method")).toBe("S256");
    const cookie = response.headers.getSetCookie().find((c) => c.startsWith("om_login_shop="))!;
    expect(cookie).toContain(`om_login_shop=${location.searchParams.get("state")}`);
    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("SameSite=Lax");
  });

  it("콜백이 세션을 만들고 HttpOnly 세션 쿠키를 주며 원래 경로로 보낸다", async () => {
    const start = await bff.login(
      new Request("http://localhost:3000/auth/login?returnTo=%2Forders%3Fpage%3D2"),
    );
    const state = new URL(start.headers.get("location")!).searchParams.get("state")!;

    const callback = await bff.callback(
      new Request(`http://localhost:3000/auth/callback?code=good-code&state=${state}`, {
        headers: { cookie: `om_login_shop=${state}` },
      }),
    );

    expect(callback.status).toBe(302);
    expect(callback.headers.get("location")).toBe("http://localhost:3000/orders?page=2");
    const sessionCookie = callback.headers
      .getSetCookie()
      .find((c) => c.startsWith("om_sid_shop="))!;
    expect(sessionCookie).toContain("HttpOnly");
    expect(sessionCookie).toContain("SameSite=Lax");
    expect(sessionCookie).toContain("Path=/");
    expect(sessionCookie).not.toContain("Secure");
    // 토큰은 쿠키에 없다.
    expect(sessionCookie).not.toContain("refresh-");
    const sid = cookieValue(callback, "om_sid_shop")!;
    expect(await bff.getSession(sid)).toEqual({ sub: "account-1", roles: ["BUYER"] });
  });

  it("쿠키의 state와 콜백 state가 다르면 세션을 만들지 않는다(로그인 CSRF)", async () => {
    const start = await bff.login(new Request("http://localhost:3000/auth/login"));
    const state = new URL(start.headers.get("location")!).searchParams.get("state")!;

    const callback = await bff.callback(
      new Request(`http://localhost:3000/auth/callback?code=good-code&state=${state}`, {
        headers: { cookie: "om_login_shop=other-state" },
      }),
    );

    expect(callback.status).toBe(400);
    expect(cookieValue(callback, "om_sid_shop")).toBeUndefined();
  });

  it("같은 콜백을 다시 보내도 세션을 또 만들지 않는다", async () => {
    const start = await bff.login(new Request("http://localhost:3000/auth/login"));
    const state = new URL(start.headers.get("location")!).searchParams.get("state")!;
    const request = () =>
      new Request(`http://localhost:3000/auth/callback?code=good-code&state=${state}`, {
        headers: { cookie: `om_login_shop=${state}` },
      });

    expect((await bff.callback(request())).status).toBe(302);
    expect((await bff.callback(request())).status).toBe(400);
  });

  it.each(["https://evil.example/x", "//evil.example/x", "/\\evil.example", "orders"])(
    "바깥 주소(%s)로는 돌려보내지 않는다",
    async (returnTo) => {
      const start = await bff.login(
        new Request(`http://localhost:3000/auth/login?returnTo=${encodeURIComponent(returnTo)}`),
      );
      const state = new URL(start.headers.get("location")!).searchParams.get("state")!;
      const callback = await bff.callback(
        new Request(`http://localhost:3000/auth/callback?code=good-code&state=${state}`, {
          headers: { cookie: `om_login_shop=${state}` },
        }),
      );

      expect(callback.headers.get("location")).toBe("http://localhost:3000/");
    },
  );

  it("auth가 access_denied로 돌려보내면 세션 없이 오류 표시로 보낸다", async () => {
    const start = await bff.login(new Request("http://localhost:3000/auth/login"));
    const state = new URL(start.headers.get("location")!).searchParams.get("state")!;

    const callback = await bff.callback(
      new Request(`http://localhost:3000/auth/callback?error=access_denied&state=${state}`, {
        headers: { cookie: `om_login_shop=${state}` },
      }),
    );

    expect(callback.status).toBe(302);
    expect(callback.headers.get("location")).toBe(
      "http://localhost:3000/?login_error=access_denied",
    );
    expect(cookieValue(callback, "om_sid_shop")).toBeUndefined();
  });
});

describe("토큰 갱신", () => {
  it("만료 전이면 저장된 액세스 토큰을 그대로 쓴다", async () => {
    const oidc = new FakeOidc(now);
    const bff = newBff(oidc);
    const sid = await login(bff);

    const token = await bff.getAccessToken(sid);

    expect(token).toContain(".");
    expect(oidc.refreshCalls).toBe(0);
  });

  it("만료가 가까우면 갱신하고, 바뀐 역할이 세션에 반영된다", async () => {
    const oidc = new FakeOidc(now);
    const bff = newBff(oidc);
    const sid = await login(bff);
    const before = await bff.getAccessToken(sid);

    oidc.roles = ["BUYER", "SELLER"];
    clock += 590_000;
    const after = await bff.getAccessToken(sid);

    expect(after).not.toBe(before);
    expect(oidc.refreshCalls).toBe(1);
    expect(await bff.getSession(sid)).toEqual({ sub: "account-1", roles: ["BUYER", "SELLER"] });
  });

  it("두 BFF 인스턴스에서 동시에 갱신해도 auth에는 한 번만 요청하고 모두 같은 새 토큰을 받는다(R8)", async () => {
    const oidc = new FakeOidc(now);
    oidc.refreshDelayMs = 200;
    const first = newBff(oidc);
    const second = newBff(oidc);
    const sid = await login(first);

    clock += 600_000;
    const tokens = await Promise.all(
      Array.from({ length: 20 }, (_, i) => (i % 2 === 0 ? first : second).getAccessToken(sid)),
    );

    expect(oidc.refreshCalls).toBe(1);
    expect(new Set(tokens).size).toBe(1);
    expect(tokens[0]).not.toBeNull();
  });

  it("리프레시 토큰이 거절되면 세션을 지우고 null을 돌려준다", async () => {
    const oidc = new FakeOidc(now);
    const bff = newBff(oidc);
    const sid = await login(bff);

    oidc.revokeAll();
    clock += 600_000;

    expect(await bff.getAccessToken(sid)).toBeNull();
    expect(await bff.getSession(sid)).toBeNull();
  });

  it("세션이 없으면 null이다", async () => {
    const bff = newBff(new FakeOidc(now));

    expect(await bff.getAccessToken("no-such-session")).toBeNull();
    expect(await bff.getAccessToken(undefined)).toBeNull();
  });
});

describe("로그아웃", () => {
  it("다른 Origin에서 온 요청은 403이다(CSRF)", async () => {
    const oidc = new FakeOidc(now);
    const bff = newBff(oidc);
    const sid = await login(bff);

    const response = await bff.logout(
      new Request("http://localhost:3000/auth/logout", {
        method: "POST",
        headers: { cookie: `om_sid_shop=${sid}`, origin: "http://evil.example" },
      }),
    );

    expect(response.status).toBe(403);
    expect(await bff.getSession(sid)).not.toBeNull();
  });

  it("세션을 지우고 리프레시 토큰을 폐기한 뒤 auth 로그아웃으로 보낸다", async () => {
    const oidc = new FakeOidc(now);
    const bff = newBff(oidc);
    const sid = await login(bff);

    const response = await bff.logout(
      new Request("http://localhost:3000/auth/logout", {
        method: "POST",
        headers: { cookie: `om_sid_shop=${sid}`, origin: "http://localhost:3000" },
      }),
    );

    expect(response.status).toBe(303);
    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/connect/logout");
    expect(location.searchParams.get("post_logout_redirect_uri")).toBe("http://localhost:3000/");
    expect(oidc.revoked).toHaveLength(1);
    expect(await bff.getSession(sid)).toBeNull();
    const cleared = response.headers.getSetCookie().find((c) => c.startsWith("om_sid_shop="))!;
    expect(cleared).toContain("Max-Age=0");
  });
});
