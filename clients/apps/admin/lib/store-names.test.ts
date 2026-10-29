import { beforeEach, describe, expect, it, vi } from "vitest";

const GET = vi.fn();
vi.mock("@/lib/bff", () => ({ bff: { serviceClient: async () => ({ GET }) } }));
const { storeNames } = await import("./store-names");

const A = "0199a000-0000-7000-8000-00000000000a";
const B = "0199a000-0000-7000-8000-00000000000b";

describe("storeNames", () => {
  beforeEach(() => {
    GET.mockReset();
  });

  it("판매자 ID를 겹치지 않게 한 번에 묻고 ID → 상호로 돌려준다", async () => {
    GET.mockResolvedValue({ data: [{ sellerId: A, storeName: "가게 A" }] });

    const names = await storeNames([A, B, A, undefined]);

    expect(GET).toHaveBeenCalledTimes(1);
    expect(GET).toHaveBeenCalledWith("/v1/admin/sellers/store-names", {
      params: { query: { ids: [A, B] } },
    });
    expect(names.get(A)).toBe("가게 A");
    expect(names.has(B)).toBe(false);
  });

  it("물을 판매자가 없으면 부르지 않는다", async () => {
    expect((await storeNames([undefined])).size).toBe(0);
    expect(GET).not.toHaveBeenCalled();
  });

  it("member에 닿지 못하면 빈 결과다(화면은 판매자 ID를 보인다)", async () => {
    GET.mockImplementation(async () => {
      throw new Error("down");
    });
    expect((await storeNames([A])).size).toBe(0);
  });
});
