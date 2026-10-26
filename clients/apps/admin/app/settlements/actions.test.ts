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

const { payOut, runSettlement } = await import("./actions");

const SETTLEMENT = "0199f5a0-0000-7000-8000-0000000000d1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

describe("payOut", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("정산 ID가 UUID가 아니면 settlement를 부르지 않는다", async () => {
    const state = await payOut(idleState, form({ settlementId: "../x" }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "정산을 찾을 수 없습니다." });
  });

  it("지급 완료로 기록하고 그 정산 상세로 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(payOut(idleState, form({ settlementId: SETTLEMENT }))).rejects.toThrow(
      `redirect:/settlements/${SETTLEMENT}?notice=paid-out`,
    );

    expect(post).toHaveBeenCalledWith("/v1/admin/settlements/{settlementId}/payout", {
      params: { path: { settlementId: SETTLEMENT } },
    });
  });

  it("settlement가 거절하면 문구를 보인다", async () => {
    post.mockResolvedValue({
      error: { code: "SETTLEMENT_NOT_FOUND", detail: "정산을 찾을 수 없습니다." },
      response: new Response(null, { status: 404 }),
    });

    const state = await payOut(idleState, form({ settlementId: SETTLEMENT }));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "정산을 찾을 수 없습니다." });
  });
});

describe("runSettlement", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("정산 작업을 바로 돌리고 확정한 건수를 정산 목록에 보인다", async () => {
    post.mockResolvedValue({
      data: { confirmedCount: 3 },
      response: new Response(null, { status: 200 }),
    });

    await expect(runSettlement()).rejects.toThrow(
      "redirect:/settlements?notice=settlement-run&confirmed=3",
    );

    expect(post).toHaveBeenCalledWith("/v1/admin/settlement-runs");
  });

  it("실패하면 문구를 보인다", async () => {
    post.mockResolvedValue({ error: {}, response: new Response(null, { status: 503 }) });

    const state = await runSettlement();

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({
      status: "error",
      message: "일시적인 오류입니다. 잠시 뒤 다시 시도해 주세요.",
    });
  });
});
