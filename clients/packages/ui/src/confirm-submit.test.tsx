import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmSubmit } from "./confirm-submit";

function renderForm(onSubmit: () => void) {
  render(
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <ConfirmSubmit
        title="탈퇴할까요?"
        description="되돌릴 수 없습니다."
        confirmText="탈퇴"
        variant="destructive"
      >
        탈퇴하기
      </ConfirmSubmit>
    </form>,
  );
}

describe("ConfirmSubmit", () => {
  it("버튼을 누르면 바로 제출하지 않고 확인을 묻는다. 확인하면 폼을 제출한다", async () => {
    const onSubmit = vi.fn();
    renderForm(onSubmit);

    await userEvent.click(screen.getByRole("button", { name: "탈퇴하기" }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole("alertdialog", { name: "탈퇴할까요?" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "탈퇴" }));
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("취소하면 제출하지 않는다", async () => {
    const onSubmit = vi.fn();
    renderForm(onSubmit);

    await userEvent.click(screen.getByRole("button", { name: "탈퇴하기" }));
    await userEvent.click(screen.getByRole("button", { name: "취소" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
