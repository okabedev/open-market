import { describe, expect, it, vi } from "vitest";
import { fetchPrivateFile, privateFilePath } from "./private-file";

const FILE = "0199f5a0-0000-7000-8000-0000000000f1";

describe("privateFilePath", () => {
  it("파일 ID와 변형이 맞을 때만 경로를 만든다", () => {
    expect(privateFilePath("user", FILE, "w300")).toBe(`/v1/files/${FILE}/w300`);
    expect(privateFilePath("admin", FILE, "w800")).toBe(`/v1/admin/files/${FILE}/w800`);
    expect(privateFilePath("user", "../etc", "w300")).toBeNull();
    expect(privateFilePath("user", FILE, "original")).toBeNull();
  });
});

describe("fetchPrivateFile", () => {
  const image = () =>
    new Response(new Uint8Array([0xff, 0xd8, 0xff]), {
      status: 200,
      headers: {
        "Content-Type": "image/jpeg",
        "Cache-Control": "private, max-age=86400",
        "Set-Cookie": "x=y",
      },
    });

  it("사용자 토큰을 붙여 file을 부르고 이미지와 캐시 헤더만 넘긴다", async () => {
    const fetch = vi.fn(async () => image());

    const response = await fetchPrivateFile(`/v1/files/${FILE}/w300`, {
      gatewayUrl: "http://gateway:8080/",
      accessToken: "token-1",
      forwardedFor: "203.0.113.7",
      fetch,
    });

    expect(fetch).toHaveBeenCalledWith(`http://gateway:8080/api/file/v1/files/${FILE}/w300`, {
      headers: { Authorization: "Bearer token-1", "X-Forwarded-For": "203.0.113.7" },
      cache: "no-store",
    });
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("image/jpeg");
    expect(response.headers.get("Cache-Control")).toBe("private, max-age=86400");
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(
      new Uint8Array([0xff, 0xd8, 0xff]),
    );
  });

  it("로그인하지 않았으면 부르지 않고 404다", async () => {
    const fetch = vi.fn(async () => image());

    const response = await fetchPrivateFile(`/v1/files/${FILE}/w300`, { accessToken: null, fetch });

    expect(fetch).not.toHaveBeenCalled();
    expect(response.status).toBe(404);
  });

  it("볼 수 없거나 실패하면 404다. 이미지가 아닌 응답은 넘기지 않는다", async () => {
    for (const upstream of [
      new Response(JSON.stringify({ code: "FILE_NOT_FOUND" }), { status: 404 }),
      new Response("oops", { status: 503 }),
      new Response("<html>", { status: 200, headers: { "Content-Type": "text/html" } }),
    ]) {
      const response = await fetchPrivateFile(`/v1/files/${FILE}/w300`, {
        accessToken: "token-1",
        fetch: async () => upstream,
      });
      expect(response.status).toBe(404);
    }
  });

  it("gateway에 닿지 못해도 404다", async () => {
    const response = await fetchPrivateFile(`/v1/files/${FILE}/w300`, {
      accessToken: "token-1",
      fetch: async () => {
        throw new TypeError("fetch failed");
      },
    });

    expect(response.status).toBe(404);
  });
});
