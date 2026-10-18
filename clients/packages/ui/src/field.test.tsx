import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TextField } from "./field";

describe("TextField", () => {
  it("이름표로 입력을 찾을 수 있고, 오류가 있으면 aria-invalid와 오류 문구를 잇는다", () => {
    render(
      <TextField name="phone" label="연락처" error="형식이 맞지 않습니다" defaultValue="010" />,
    );

    const input = screen.getByLabelText("연락처");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("형식이 맞지 않습니다");
    expect(input).toHaveValue("010");
  });

  it("오류가 없으면 안내 문구를 설명으로 잇는다", () => {
    render(<TextField name="phone" label="연락처" hint="010-1234-5678 형식" />);

    const input = screen.getByLabelText("연락처");
    expect(input).not.toHaveAttribute("aria-invalid");
    expect(input).toHaveAccessibleDescription("010-1234-5678 형식");
  });
});
