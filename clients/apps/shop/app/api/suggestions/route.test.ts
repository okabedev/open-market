import { beforeEach, describe, expect, it, vi } from "vitest";

const get = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { publicClient: vi.fn(async () => ({ GET: get })) },
}));

const { GET } = await import("./route");

function request(q?: string): Request {
  const url = new URL("http://shop.local/api/suggestions");
  if (q !== undefined) {
    url.searchParams.set("q", q);
  }
  return new Request(url);
}

describe("GET /api/suggestions", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("검색어를 다듬어 search 자동완성을 부르고 상품명만 돌려준다. 잠깐 캐시한다", async () => {
    get.mockResolvedValue({
      data: { items: [{ name: "반팔 티셔츠" }, { name: "반팔 셔츠" }] },
      response: new Response(null, { status: 200 }),
    });

    const response = await GET(request("  반팔 "));

    expect(get).toHaveBeenCalledWith("/v1/suggestions", { params: { query: { q: "반팔" } } });
    expect(await response.json()).toEqual({ items: ["반팔 티셔츠", "반팔 셔츠"] });
    expect(response.headers.get("Cache-Control")).toBe("public, max-age=60");
  });

  it("검색어가 비었거나 50자를 넘으면 부르지 않고 빈 목록이다", async () => {
    for (const q of [undefined, " ", "가".repeat(51)]) {
      expect(await (await GET(request(q))).json()).toEqual({ items: [] });
    }
    expect(get).not.toHaveBeenCalled();
  });

  it("search에 닿지 못하거나 오류면 빈 목록이다(검색창은 그대로 쓴다)", async () => {
    get.mockRejectedValueOnce(new Error("down"));
    expect(await (await GET(request("반팔"))).json()).toEqual({ items: [] });

    get.mockResolvedValueOnce({ data: undefined, response: new Response(null, { status: 503 }) });
    const response = await GET(request("반팔"));
    expect(await response.json()).toEqual({ items: [] });
    expect(response.headers.get("Cache-Control")).toBe("no-store");
  });
});
