import { describe, expect, it } from "vitest";
import { fieldErrorsFrom, problemState } from "./form-state";

describe("problemState", () => {
  it("서비스가 준 detail과 필드 오류를 그대로 쓴다", () => {
    const state = problemState(
      {
        detail: "입력값을 확인해 주세요.",
        code: "COMMON_INVALID",
        errors: [
          { field: "email", message: "이메일 형식이 아닙니다" },
          { field: "email", message: "두 번째 오류" },
        ],
      },
      400,
      { email: "bad" },
    );

    expect(state).toEqual({
      status: "error",
      message: "입력값을 확인해 주세요.",
      fieldErrors: { email: "이메일 형식이 아닙니다" },
      values: { email: "bad" },
    });
  });

  it("본문이 없는 gateway 응답(429)은 상태별 기본 문구를 쓴다", () => {
    expect(problemState(undefined, 429).message).toBe(
      "요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요.",
    );
    expect(problemState(undefined, 503).message).toContain("일시적인 오류");
  });
});

describe("fieldErrorsFrom", () => {
  it("경로를 필드 이름으로 쓰고 필드마다 첫 오류만 남긴다", () => {
    expect(
      fieldErrorsFrom([
        { path: ["name"], message: "이름을 입력해 주세요" },
        { path: ["name"], message: "두 번째" },
        { path: ["address", "zip"], message: "우편번호" },
      ]),
    ).toEqual({ name: "이름을 입력해 주세요", "address.zip": "우편번호" });
  });
});
