import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const put = vi.fn();
const del = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ PUT: put, DELETE: del })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { resetCommissionRate, setCommissionRate } = await import("./actions");

const CATEGORY = "0199f000-0000-7000-8000-000000000101";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

describe("setCommissionRate", () => {
  beforeEach(() => {
    put.mockReset();
    redirect.mockClear();
  });

  it("카테고리 ID가 UUID가 아니면 settlement를 부르지 않는다", async () => {
    const state = await setCommissionRate(idleState, form({ categoryId: "../x", rate: "8" }));

    expect(put).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "카테고리를 찾을 수 없습니다." });
  });

  it("요율이 0~100%가 아니거나 소수 셋째 자리면 정하지 않고 입력을 남긴다", async () => {
    const state = await setCommissionRate(idleState, form({ categoryId: CATEGORY, rate: "8.125" }));

    expect(put).not.toHaveBeenCalled();
    expect(state).toEqual({
      status: "error",
      fieldErrors: { rate: "요율은 0~100%, 소수 둘째 자리까지입니다." },
      values: { rate: "8.125" },
    });
  });

  it("%를 bp로 바꿔 정하고 요율 화면으로 돌아간다", async () => {
    put.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      setCommissionRate(idleState, form({ categoryId: CATEGORY, rate: "8.5" })),
    ).rejects.toThrow("redirect:/commission-rates?notice=commission-set");

    expect(put).toHaveBeenCalledWith("/v1/admin/commission-rates/{categoryId}", {
      params: { path: { categoryId: CATEGORY } },
      body: { rateBp: 850 },
    });
  });

  it("settlement가 거절하면 문구를 보인다", async () => {
    put.mockResolvedValue({
      error: { code: "SETTLEMENT_INVALID_INPUT", detail: "요율이 범위를 벗어났습니다." },
      response: new Response(null, { status: 400 }),
    });

    const state = await setCommissionRate(idleState, form({ categoryId: CATEGORY, rate: "8" }));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "요율이 범위를 벗어났습니다." });
  });
});

describe("resetCommissionRate", () => {
  beforeEach(() => {
    del.mockReset();
    redirect.mockClear();
  });

  it("요율을 지워 기본 요율로 돌리고 요율 화면으로 돌아간다", async () => {
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(resetCommissionRate(idleState, form({ categoryId: CATEGORY }))).rejects.toThrow(
      "redirect:/commission-rates?notice=commission-reset",
    );

    expect(del).toHaveBeenCalledWith("/v1/admin/commission-rates/{categoryId}", {
      params: { path: { categoryId: CATEGORY } },
    });
  });
});
