// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

const signup = vi.fn();
vi.mock("./actions", () => ({ signup: (...args: unknown[]) => signup(...args) }));

const { SignupForm } = await import("./signup-form");

afterEach(() => {
  cleanup();
});

describe("SignupForm", () => {
  it("제출하면 Server Action의 필드 오류를 입력 옆에 보여 주고 입력한 값을 남긴다", async () => {
    signup.mockResolvedValue({
      status: "error",
      fieldErrors: { email: "이메일 형식이 아닙니다." },
      values: { name: "홍길동", email: "nope" },
    });
    render(<SignupForm />);

    await userEvent.type(screen.getByLabelText("이름"), "홍길동");
    await userEvent.type(screen.getByLabelText("이메일"), "nope");
    await userEvent.click(screen.getByRole("button", { name: "가입하기" }));

    const email = await screen.findByLabelText("이메일");
    expect(email).toHaveAccessibleDescription("이메일 형식이 아닙니다.");
    expect(email).toHaveValue("nope");
    expect(screen.getByLabelText("비밀번호")).toHaveValue("");
    expect(signup).toHaveBeenCalledOnce();
  });
});
