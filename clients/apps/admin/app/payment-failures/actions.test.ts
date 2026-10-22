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

const { retryCancel, retryRefund } = await import("./actions");

const ID = "0199f5a0-0000-7000-8000-0000000000d1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

beforeEach(() => {
  post.mockReset();
  post.mockResolvedValue({ response: new Response(null, { status: 204 }) });
});

describe("retryRefund", () => {
  it("환불을 다시 시도하고 같은 목록으로 돌아간다", async () => {
    await expect(
      retryRefund(idleState, form({ refundId: ID, returnTo: "/payment-failures?status=REFUND" })),
    ).rejects.toThrow("redirect:/payment-failures?status=REFUND&notice=refund-retried");
    expect(post).toHaveBeenCalledWith("/v1/admin/refunds/{refundId}/retry", {
      params: { path: { refundId: ID } },
    });
  });

  it("다시 확인을 통과하지 못하면 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "환불할 수 없는 결제입니다.", code: "PAYMENT_NOT_REFUNDABLE" },
      response: new Response(null, { status: 409 }),
    });

    const state = await retryRefund(idleState, form({ refundId: ID }));

    expect(state).toMatchObject({ status: "error", message: "환불할 수 없는 결제입니다." });
  });
});

describe("retryCancel", () => {
  it("보상 취소를 다시 시도한다", async () => {
    await expect(retryCancel(idleState, form({ paymentId: ID }))).rejects.toThrow(
      "redirect:/payment-failures?notice=cancel-retried",
    );
    expect(post).toHaveBeenCalledWith("/v1/admin/payments/{paymentId}/cancel-retry", {
      params: { path: { paymentId: ID } },
    });
  });

  it("ID가 아니면 부르지 않는다", async () => {
    const state = await retryCancel(idleState, form({ paymentId: "x" }));

    expect(state.status).toBe("error");
    expect(post).not.toHaveBeenCalled();
  });
});
