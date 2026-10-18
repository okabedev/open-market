import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { publicClient: vi.fn(async () => ({ POST: post })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { signup } = await import("./actions");

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(values)) {
    data.set(name, value);
  }
  return data;
}

const VALID = { name: "홍길동", email: "buyer@example.com", password: "buyer-pass-1234" };

describe("signup", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("입력 규칙에 맞지 않으면 auth를 부르지 않고 필드 오류를 돌려준다. 비밀번호는 되돌려 주지 않는다", async () => {
    const state = await signup(idleState, form({ name: " ", email: "nope", password: "short" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.status).toBe("error");
    expect(Object.keys(state.fieldErrors ?? {})).toEqual(["name", "email", "password"]);
    expect(state.values).toEqual({ name: " ", email: "nope" });
  });

  it("auth가 거절하면 ProblemDetail 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "이미 가입한 이메일입니다.", code: "AUTH_EMAIL_TAKEN" },
      response: new Response(null, { status: 409 }),
    });

    const state = await signup(idleState, form(VALID));

    expect(state).toMatchObject({ status: "error", message: "이미 가입한 이메일입니다." });
  });

  it("가입하면 이름·이메일·비밀번호를 보내고 안내 화면으로 보낸다", async () => {
    post.mockResolvedValue({
      data: { accountId: "a-1" },
      response: new Response(null, { status: 201 }),
    });

    await expect(signup(idleState, form(VALID))).rejects.toThrow("redirect:/signup/done");

    expect(post).toHaveBeenCalledWith("/v1/accounts", { body: VALID });
  });
});
