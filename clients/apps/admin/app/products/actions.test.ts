import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { ban } = await import("./actions");

const PRODUCT = "0199f5a0-0000-7000-8000-0000000000b1";

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(values)) {
    data.set(name, value);
  }
  return data;
}

describe("ban", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("사유가 없으면 catalog를 부르지 않는다", async () => {
    const state = await ban(idleState, form({ productId: PRODUCT, reason: " " }));

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.reason).toBe("사유를 입력해 주세요.");
  });

  it("상품 ID가 UUID가 아니면 부르지 않는다", async () => {
    const state = await ban(idleState, form({ productId: "../sellers", reason: "허위 광고" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.status).toBe("error");
  });

  it("제재하고 같은 목록 조건으로 돌아가 결과를 알린다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      ban(
        idleState,
        form({
          productId: PRODUCT,
          reason: " 허위 광고 ",
          returnTo: "/products?status=ON_SALE&sellerId=s-1&page=1",
        }),
      ),
    ).rejects.toThrow("redirect:/products?status=ON_SALE&sellerId=s-1&page=1&notice=banned");

    expect(post).toHaveBeenCalledWith("/v1/admin/products/{productId}/ban", {
      params: { path: { productId: PRODUCT } },
      body: { reason: "허위 광고" },
    });
  });
});
