import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TextAreaField, TextField } from "./field";

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

describe("TextAreaField", () => {
  it("같은 이름의 필드가 한 화면에 여럿이면 id를 따로 주어 이름표가 자기 입력을 가리킨다", () => {
    render(
      <>
        <section aria-label="첫째">
          <TextAreaField id="reason-a" name="reason" label="사유" error="입력해 주세요" />
        </section>
        <section aria-label="둘째">
          <TextAreaField id="reason-b" name="reason" label="사유" defaultValue="허위 광고" />
        </section>
      </>,
    );

    const second = within(screen.getByRole("region", { name: "둘째" })).getByLabelText("사유");
    expect(second).toHaveValue("허위 광고");
    expect(second).toHaveAttribute("name", "reason");
    const first = within(screen.getByRole("region", { name: "첫째" })).getByLabelText("사유");
    expect(first).toHaveAccessibleDescription("입력해 주세요");
  });
});
