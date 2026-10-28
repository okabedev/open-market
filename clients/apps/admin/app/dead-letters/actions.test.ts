import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const adminClient = vi.fn(async (service: string) => {
  void service;
  return { POST: post };
});
vi.mock("@/lib/admin-client", () => ({ adminClient: (service: string) => adminClient(service) }));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { discardDeadLetter, retryDeadLetter } = await import("./actions");

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
  adminClient.mockClear();
  post.mockResolvedValue({ response: new Response(null, { status: 204 }) });
});

describe("retryDeadLetter", () => {
  it("메시지를 모은 서비스에 다시 보내기를 요청하고 같은 목록으로 돌아간다", async () => {
    await expect(
      retryDeadLetter(
        idleState,
        form({ service: "payment", id: ID, returnTo: "/dead-letters?service=payment" }),
      ),
    ).rejects.toThrow("redirect:/dead-letters?service=payment&notice=dead-letter-retried");
    expect(adminClient).toHaveBeenCalledWith("payment");
    expect(post).toHaveBeenCalledWith("/v1/admin/dead-letters/{id}/retry", {
      params: { path: { id: ID } },
    });
  });

  it("이미 처리한 행이거나 전송이 실패하면 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "이미 처리한 메시지입니다.", code: "DEAD_LETTER_NOT_PENDING" },
      response: new Response(null, { status: 409 }),
    });

    const state = await retryDeadLetter(idleState, form({ service: "order", id: ID }));

    expect(state).toMatchObject({ status: "error", message: "이미 처리한 메시지입니다." });
  });

  it("모르는 서비스나 ID면 부르지 않는다", async () => {
    const state = await retryDeadLetter(idleState, form({ service: "evil", id: ID }));

    expect(state).toMatchObject({ status: "error" });
    expect(post).not.toHaveBeenCalled();
  });
});

describe("discardDeadLetter", () => {
  it("사유와 함께 버린다", async () => {
    await expect(
      discardDeadLetter(
        idleState,
        form({ service: "order", id: ID, reason: " 테스트 데이터라 처리하지 않음 " }),
      ),
    ).rejects.toThrow("redirect:/dead-letters?notice=dead-letter-discarded");
    expect(post).toHaveBeenCalledWith("/v1/admin/dead-letters/{id}/discard", {
      params: { path: { id: ID } },
      body: { reason: "테스트 데이터라 처리하지 않음" },
    });
  });

  it("사유가 없으면 부르지 않고 입력 오류다", async () => {
    const state = await discardDeadLetter(
      idleState,
      form({ service: "order", id: ID, reason: " " }),
    );

    expect(state.fieldErrors?.reason).toBeDefined();
    expect(post).not.toHaveBeenCalled();
  });
});
