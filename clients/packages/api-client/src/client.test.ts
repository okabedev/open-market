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

describe("createServiceClient", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("gateway의 /api/<서비스> 아래로 요청하고 응답 본문을 data로 돌려준다", async () => {
    const { requests, fetch } = recordingFetch({ service: "member" });
    const member = createServiceClient("member", { gatewayUrl: "http://gateway.test/", fetch });

    const { data, error } = await member.GET("/v1/ping");

    expect(requests[0]?.method).toBe("GET");
    expect(requests[0]?.url).toBe("http://gateway.test/api/member/v1/ping");
    expect(error).toBeUndefined();
    expect(data?.service).toBe("member");
  });

  it("2xx가 아닌 응답은 error로 돌려준다", async () => {
    const { fetch } = recordingFetch({ title: "Not Found" }, 404);
    const member = createServiceClient("member", { gatewayUrl: "http://gateway.test", fetch });

    const { data, error, response } = await member.POST("/v1/ping");

    expect(response.status).toBe(404);
    expect(data).toBeUndefined();
    expect(error).toEqual({ title: "Not Found" });
  });

  it("주소를 주지 않으면 GATEWAY_URL 환경 변수를 쓴다", async () => {
    vi.stubEnv("GATEWAY_URL", "http://gateway.env:8080");
    const { requests, fetch } = recordingFetch({ pingId: "019a0f3c-5e2b-7c41-9d3e-2f6a8b1c4d70" });
    const member = createServiceClient("member", { fetch });

    await member.POST("/v1/ping");

    expect(requests[0]?.url).toBe("http://gateway.env:8080/api/member/v1/ping");
  });
});

describe("패키지 진입점", () => {
  it("서버 밖(react-server 조건이 아닌 곳)에서 불러오면 실패한다", async () => {
    await expect(import("./index")).rejects.toThrow();
  });
});
