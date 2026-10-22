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

const { approveMediation, rejectMediation } = await import("./actions");

const CLAIM = "0199f5a0-0000-7000-8000-0000000000c1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

beforeEach(() => {
  post.mockReset();
  redirect.mockClear();
});

describe("approveMediation", () => {
  it("고른 귀책으로 승인하고 중재 목록으로 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      approveMediation(
        idleState,
        form({ claimId: CLAIM, type: "RETURN", fault: "BUYER", returnTo: "/claims?page=1" }),
      ),
    ).rejects.toThrow("redirect:/claims?page=1&notice=mediation-approved");
    expect(post).toHaveBeenCalledWith("/v1/admin/claims/{claimId}/approve", {
      params: { path: { claimId: CLAIM } },
      body: { fault: "BUYER" },
    });
  });

  it("귀책을 고르지 않았거나, 교환인데 구매자 귀책이면 부르지 않는다", async () => {
    const none = await approveMediation(idleState, form({ claimId: CLAIM, type: "RETURN" }));
    expect(none.fieldErrors?.fault).toBe("귀책을 골라 주세요.");

    const exchange = await approveMediation(
      idleState,
      form({ claimId: CLAIM, type: "EXCHANGE", fault: "BUYER" }),
    );
    expect(exchange.fieldErrors?.fault).toBe("교환은 판매자 귀책일 때만 승인할 수 있습니다.");
    expect(post).not.toHaveBeenCalled();
  });

  it("그사이 다른 관리자가 결정했으면 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "지금 상태에서는 할 수 없습니다.", code: "FULFILLMENT_CLAIM_INVALID_STATE" },
      response: new Response(null, { status: 409 }),
    });

    const state = await approveMediation(
      idleState,
      form({ claimId: CLAIM, type: "RETURN", fault: "SELLER" }),
    );

    expect(state).toMatchObject({ status: "error", message: "지금 상태에서는 할 수 없습니다." });
  });
});

describe("rejectMediation", () => {
  it("사유를 남기고 거부를 확정한다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      rejectMediation(
        idleState,
        form({ claimId: CLAIM, reason: " 사진으로 불량을 확인할 수 없습니다 " }),
      ),
    ).rejects.toThrow("redirect:/claims?notice=mediation-rejected");
    expect(post).toHaveBeenCalledWith("/v1/admin/claims/{claimId}/reject", {
      params: { path: { claimId: CLAIM } },
      body: { reason: "사진으로 불량을 확인할 수 없습니다" },
    });
  });

  it("사유가 없으면 부르지 않는다", async () => {
    const state = await rejectMediation(idleState, form({ claimId: CLAIM, reason: " " }));

    expect(state.fieldErrors?.reason).toBe("사유를 입력해 주세요.");
    expect(post).not.toHaveBeenCalled();
  });
});
