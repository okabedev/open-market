import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const getAccessToken = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: {
    getAccessToken: () => getAccessToken(),
    getSession: vi.fn(async () => ({ roles: ["BUYER"] })),
    serviceClient: vi.fn(async () => ({ POST: post })),
  },
}));

const { POST } = await import("./route");

function upload(file: Blob | string | null): Request {
  const body = new FormData();
  if (file !== null) {
    body.set("file", file);
  }
  return new Request("http://shop.local/api/files", { method: "POST", body });
}

describe("POST /api/files", () => {
  beforeEach(() => {
    post.mockReset();
    getAccessToken.mockResolvedValue("token");
  });

  it("로그인하지 않았으면 401이고 file을 부르지 않는다", async () => {
    getAccessToken.mockResolvedValue(null);

    const response = await POST(upload(new File(["x"], "a.jpg")));

    expect(response.status).toBe(401);
    expect(post).not.toHaveBeenCalled();
  });

  it("파일이 없거나 10MB를 넘으면 400이다", async () => {
    expect((await POST(upload(null))).status).toBe(400);
    const large = new File([new Uint8Array(10 * 1024 * 1024 + 1)], "big.jpg");
    const response = await POST(upload(large));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ detail: "이미지는 10MB까지 올릴 수 있습니다." });
    expect(post).not.toHaveBeenCalled();
  });

  it("클레임 사진 용도로 file에 넘기고 fileId만 돌려준다", async () => {
    post.mockResolvedValue({
      data: { fileId: "f-1", width: 800, height: 600, variants: [] },
      response: new Response(null, { status: 201 }),
    });

    const response = await POST(upload(new File(["jpeg"], "a.jpg", { type: "image/jpeg" })));

    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ fileId: "f-1" });
    const [path, options] = post.mock.calls[0] as [
      string,
      { params: unknown; bodySerializer: (body: unknown) => FormData },
    ];
    expect(path).toBe("/v1/files");
    expect(options.params).toEqual({ query: { purpose: "CLAIM_IMAGE" } });
    expect(options.bodySerializer({}).get("file")).toBeInstanceOf(File);
  });

  it("file이 거절하면 상태와 문구를 그대로 넘긴다", async () => {
    post.mockResolvedValue({
      error: {
        detail: "JPEG, PNG, WebP 이미지만 올릴 수 있습니다.",
        code: "FILE_UNSUPPORTED_FORMAT",
      },
      response: new Response(null, { status: 400 }),
    });

    const response = await POST(upload(new File(["gif"], "a.gif")));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ detail: "JPEG, PNG, WebP 이미지만 올릴 수 있습니다." });
  });
});
