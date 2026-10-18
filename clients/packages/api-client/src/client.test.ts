import { afterEach, describe, expect, it, vi } from "vitest";
import { createServiceClient } from "./client";

function recordingFetch(body: unknown, status = 200) {
  const requests: Request[] = [];
  const fetch = async (request: Request) => {
    requests.push(request);
    return Response.json(body, { status });
  };
  return { requests, fetch };
}

const ACCOUNT_ID = "019a0f3c-5e2b-7c41-9d3e-2f6a8b1c4d70";
const SIGNUP = { email: "buyer@example.com", password: "password-1234", name: "구매자" };

describe("createServiceClient", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("gateway의 /api/<서비스> 아래로 요청하고 응답 본문을 data로 돌려준다", async () => {
    const { requests, fetch } = recordingFetch({ accountId: ACCOUNT_ID }, 201);
    const auth = createServiceClient("auth", { gatewayUrl: "http://gateway.test/", fetch });

    const { data, error } = await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(requests[0]?.method).toBe("POST");
    expect(requests[0]?.url).toBe("http://gateway.test/api/auth/v1/accounts");
    expect(await requests[0]?.json()).toEqual(SIGNUP);
    expect(error).toBeUndefined();
    expect(data?.accountId).toBe(ACCOUNT_ID);
  });

  it("2xx가 아닌 응답은 error로 돌려준다", async () => {
    const { fetch } = recordingFetch({ title: "Conflict" }, 409);
    const auth = createServiceClient("auth", { gatewayUrl: "http://gateway.test", fetch });

    const { data, error, response } = await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(response.status).toBe(409);
    expect(data).toBeUndefined();
    expect(error).toEqual({ title: "Conflict" });
  });

  it("액세스 토큰을 주면 Authorization 헤더에 Bearer로 붙인다", async () => {
    const { requests, fetch } = recordingFetch({ accountId: ACCOUNT_ID }, 201);
    const auth = createServiceClient("auth", {
      gatewayUrl: "http://gateway.test",
      fetch,
      accessToken: "token-1",
    });

    await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(requests[0]?.headers.get("authorization")).toBe("Bearer token-1");
  });

  it("토큰이 없으면 Authorization 헤더를 붙이지 않는다", async () => {
    const { requests, fetch } = recordingFetch({ accountId: ACCOUNT_ID }, 201);
    const auth = createServiceClient("auth", { gatewayUrl: "http://gateway.test", fetch });

    await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(requests[0]?.headers.get("authorization")).toBeNull();
  });

  it("주소를 주지 않으면 GATEWAY_URL 환경 변수를 쓴다", async () => {
    vi.stubEnv("GATEWAY_URL", "http://gateway.env:8080");
    const { requests, fetch } = recordingFetch({ accountId: ACCOUNT_ID }, 201);
    const auth = createServiceClient("auth", { fetch });

    await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(requests[0]?.url).toBe("http://gateway.env:8080/api/auth/v1/accounts");
  });

  it("forwardedFor를 주면 X-Forwarded-For로 붙인다(gateway의 IP별 레이트 리밋)", async () => {
    const { requests, fetch } = recordingFetch({ accountId: ACCOUNT_ID }, 201);
    const auth = createServiceClient("auth", {
      gatewayUrl: "http://gateway.test",
      fetch,
      forwardedFor: "203.0.113.7",
    });

    await auth.POST("/v1/accounts", { body: SIGNUP });

    expect(requests[0]?.headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });
});

describe("패키지 진입점", () => {
  it("서버 밖(react-server 조건이 아닌 곳)에서 불러오면 실패한다", async () => {
    await expect(import("./index")).rejects.toThrow();
  });
});
