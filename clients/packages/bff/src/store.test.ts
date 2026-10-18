import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { RedisSessionStore } from "./store";
import { startRedis } from "./test-support";
import type { Session } from "./session";

let redis: Awaited<ReturnType<typeof startRedis>>;
let store: RedisSessionStore;

const session: Session = {
  sub: "account-1",
  roles: ["BUYER"],
  accessToken: "a",
  accessTokenExpiresAt: 1,
  refreshToken: "r",
  idToken: "i",
  createdAt: 0,
};

beforeAll(async () => {
  redis = await startRedis();
  store = new RedisSessionStore(redis.client, "shop");
});

afterAll(async () => {
  await redis.client.close();
  await redis.container.stop();
});

describe("RedisSessionStore", () => {
  it("세션을 앱 이름이 붙은 키에 TTL과 함께 저장하고 지운다", async () => {
    await store.save("sid-1", session, 60);

    expect(await store.get("sid-1")).toEqual(session);
    expect(await redis.client.ttl("bff:shop:session:sid-1")).toBeGreaterThan(55);

    await store.delete("sid-1");
    expect(await store.get("sid-1")).toBeNull();
  });

  it("다른 앱의 저장소는 같은 세션 ID라도 서로 보지 못한다", async () => {
    await store.save("sid-2", session, 60);

    expect(await new RedisSessionStore(redis.client, "seller").get("sid-2")).toBeNull();
  });

  it("로그인 상태는 한 번만 꺼낼 수 있다", async () => {
    await store.saveLogin("state-1", { codeVerifier: "v", nonce: "n", returnTo: "/" }, 60);

    expect(await store.takeLogin("state-1")).toEqual({
      codeVerifier: "v",
      nonce: "n",
      returnTo: "/",
    });
    expect(await store.takeLogin("state-1")).toBeNull();
  });

  it("갱신 락은 한 곳만 잡고, 잡은 쪽만 풀 수 있다", async () => {
    expect(await store.lock("sid-3", "owner-a", 5_000)).toBe(true);
    expect(await store.lock("sid-3", "owner-b", 5_000)).toBe(false);

    await store.unlock("sid-3", "owner-b");
    expect(await store.lock("sid-3", "owner-b", 5_000)).toBe(false);

    await store.unlock("sid-3", "owner-a");
    expect(await store.lock("sid-3", "owner-b", 5_000)).toBe(true);
  });
});
