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

const { acceptOrder, approveClaim, rejectClaim, shipOrder } = await import("./actions");

const SELLER_ORDER = "0199f5a0-0000-7000-8000-0000000000a1";
const CLAIM = "0199f5a0-0000-7000-8000-0000000000c1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

const ok = () => ({ response: new Response(null, { status: 204 }) });

beforeEach(() => {
  post.mockReset();
  redirect.mockClear();
});

describe("acceptOrder", () => {
  it("발주를 확인하고 하위 주문 상세로 돌아간다", async () => {
    post.mockResolvedValue(ok());

    await expect(acceptOrder(idleState, form({ sellerOrderId: SELLER_ORDER }))).rejects.toThrow(
      `redirect:/orders/${SELLER_ORDER}?notice=accepted`,
    );
    expect(post).toHaveBeenCalledWith("/v1/seller/seller-orders/{sellerOrderId}/accept", {
      params: { path: { sellerOrderId: SELLER_ORDER } },
    });
  });

  it("하위 주문 ID가 아니면 부르지 않는다", async () => {
    const state = await acceptOrder(idleState, form({ sellerOrderId: "../x" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.status).toBe("error");
  });

  it("그사이 상태가 바뀌었으면(409) 다시 읽도록 상세로 돌아가 알린다", async () => {
    post.mockResolvedValue({
      error: { code: "FULFILLMENT_INVALID_STATE", detail: "지금 상태에서는 할 수 없습니다." },
      response: new Response(null, { status: 409 }),
    });

    await expect(acceptOrder(idleState, form({ sellerOrderId: SELLER_ORDER }))).rejects.toThrow(
      `redirect:/orders/${SELLER_ORDER}?notice=conflict`,
    );
  });
});

describe("shipOrder", () => {
  it("택배사와 하이픈 뺀 송장 번호로 발송한다", async () => {
    post.mockResolvedValue(ok());

    await expect(
      shipOrder(
        idleState,
        form({
          sellerOrderId: SELLER_ORDER,
          carrier: "CJ_LOGISTICS",
          trackingNumber: " 1234-5678-9012 ",
        }),
      ),
    ).rejects.toThrow(`redirect:/orders/${SELLER_ORDER}?notice=shipped`);
    expect(post).toHaveBeenCalledWith("/v1/seller/seller-orders/{sellerOrderId}/ship", {
      params: { path: { sellerOrderId: SELLER_ORDER } },
      body: { carrier: "CJ_LOGISTICS", trackingNumber: "123456789012" },
    });
  });

  it("택배사를 고르지 않았거나 송장 번호가 숫자 10~14자리가 아니면 부르지 않는다", async () => {
    const state = await shipOrder(
      idleState,
      form({ sellerOrderId: SELLER_ORDER, carrier: "", trackingNumber: "12345" }),
    );

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors).toEqual({
      carrier: "택배사를 골라 주세요.",
      trackingNumber: "송장 번호는 숫자 10~14자리입니다.",
    });
    expect(state.values).toEqual({ carrier: "", trackingNumber: "12345" });
  });

  it("서비스 검증 오류는 폼에 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "입력값을 확인해 주세요." },
      response: new Response(null, { status: 400 }),
    });

    const state = await shipOrder(
      idleState,
      form({ sellerOrderId: SELLER_ORDER, carrier: "HANJIN", trackingNumber: "1234567890" }),
    );

    expect(state).toMatchObject({ status: "error", message: "입력값을 확인해 주세요." });
  });
});

describe("approveClaim", () => {
  it("반품은 재입고 여부를 함께 보낸다", async () => {
    post.mockResolvedValue(ok());

    await expect(
      approveClaim(
        idleState,
        form({ sellerOrderId: SELLER_ORDER, claimId: CLAIM, type: "RETURN", restock: "false" }),
      ),
    ).rejects.toThrow(`redirect:/orders/${SELLER_ORDER}?notice=claim-approved`);
    expect(post).toHaveBeenCalledWith("/v1/seller/claims/{claimId}/approve", {
      params: { path: { claimId: CLAIM } },
      body: { restock: false },
    });
  });

  it("반품인데 재입고 여부를 고르지 않으면 부르지 않는다", async () => {
    const state = await approveClaim(
      idleState,
      form({ sellerOrderId: SELLER_ORDER, claimId: CLAIM, type: "RETURN" }),
    );

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.restock).toBe("재입고할지 골라 주세요.");
  });

  it("교환은 재입고 없이 승인한다", async () => {
    post.mockResolvedValue(ok());

    await expect(
      approveClaim(
        idleState,
        form({ sellerOrderId: SELLER_ORDER, claimId: CLAIM, type: "EXCHANGE" }),
      ),
    ).rejects.toThrow("redirect:");
    expect(post).toHaveBeenCalledWith("/v1/seller/claims/{claimId}/approve", {
      params: { path: { claimId: CLAIM } },
      body: {},
    });
  });
});

describe("rejectClaim", () => {
  it("앞뒤 공백을 지운 사유로 거부한다", async () => {
    post.mockResolvedValue(ok());

    await expect(
      rejectClaim(
        idleState,
        form({ sellerOrderId: SELLER_ORDER, claimId: CLAIM, reason: "  개봉한 상품입니다 " }),
      ),
    ).rejects.toThrow(`redirect:/orders/${SELLER_ORDER}?notice=claim-rejected`);
    expect(post).toHaveBeenCalledWith("/v1/seller/claims/{claimId}/reject", {
      params: { path: { claimId: CLAIM } },
      body: { reason: "개봉한 상품입니다" },
    });
  });

  it("사유가 없거나 500자를 넘으면 부르지 않는다", async () => {
    for (const reason of [" ", "가".repeat(501)]) {
      const state = await rejectClaim(
        idleState,
        form({ sellerOrderId: SELLER_ORDER, claimId: CLAIM, reason }),
      );
      expect(state.fieldErrors?.reason).toBeDefined();
    }
    expect(post).not.toHaveBeenCalled();
  });
});
