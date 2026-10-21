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

const { cancelOrder } = await import("./actions");

const ORDER = "0199f5a0-0000-7000-8000-0000000000a1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

describe("cancelOrder", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("주문 ID가 UUID가 아니면 order를 부르지 않는다", async () => {
    const state = await cancelOrder(idleState, form({ orderId: "../cart" }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "주문을 찾을 수 없습니다." });
  });

  it("취소하면 같은 주문 상세로 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(cancelOrder(idleState, form({ orderId: ORDER }))).rejects.toThrow(
      `redirect:/orders/${ORDER}`,
    );
    expect(post).toHaveBeenCalledWith("/v1/orders/{orderId}/cancel", {
      params: { path: { orderId: ORDER } },
    });
  });

  it("그 사이 결제가 끝나 취소할 수 없으면 order의 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { code: "ORDER_NOT_CANCELLABLE", detail: "결제 전 주문만 취소할 수 있습니다." },
      response: new Response(null, { status: 409 }),
    });

    const state = await cancelOrder(idleState, form({ orderId: ORDER }));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "결제 전 주문만 취소할 수 있습니다." });
  });
});
