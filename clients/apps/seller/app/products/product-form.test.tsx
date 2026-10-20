// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { idleState } from "@openmarket/ui";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { newRow, ProductForm } from "./product-form";

afterEach(() => {
  cleanup();
});

const CATEGORIES = [
  {
    categoryId: "top",
    name: "패션의류",
    children: [{ categoryId: "leaf", name: "여성의류" }],
  },
];

function renderCreate() {
  const action = vi.fn(async () => idleState);
  const { container } = render(
    <ProductForm
      mode="create"
      action={action}
      categories={CATEGORIES}
      initial={{ imageFileIds: [], optionNames: [], skus: [newRow([])] }}
      hidden={{ idempotencyKey: "key-1" }}
      submitText="등록"
    />,
  );
  const hidden = (name: string) =>
    JSON.parse((container.querySelector(`input[name="${name}"]`) as HTMLInputElement).value);
  return { hidden };
}

describe("ProductForm", () => {
  it("옵션이 없으면 SKU 한 줄로 시작하고, 옵션 값을 쉼표로 적어 조합을 만들면 줄이 바뀐다", async () => {
    const user = userEvent.setup();
    const { hidden } = renderCreate();
    expect(hidden("skus")).toEqual([{ optionValues: [], price: null, initialStock: 0 }]);

    await user.click(screen.getByRole("button", { name: "옵션 추가" }));
    await user.type(screen.getByLabelText("옵션 1 이름"), "색상");
    await user.type(screen.getByLabelText("값(쉼표로 구분)"), "블랙, 화이트,블랙");
    await user.click(screen.getByRole("button", { name: "조합 만들기" }));

    expect(hidden("optionNames")).toEqual(["색상"]);
    expect(hidden("skus").map((sku: { optionValues: string[] }) => sku.optionValues)).toEqual([
      ["블랙"],
      ["화이트"],
    ]);
  });

  it("가격 일괄 입력은 모든 줄에 적용된다", async () => {
    const user = userEvent.setup();
    const { hidden } = renderCreate();
    await user.click(screen.getByRole("button", { name: "옵션 추가" }));
    await user.type(screen.getByLabelText("옵션 1 이름"), "사이즈");
    await user.type(screen.getByLabelText("값(쉼표로 구분)"), "M, L");
    await user.click(screen.getByRole("button", { name: "조합 만들기" }));

    await user.type(screen.getByLabelText("가격 일괄 입력"), "19900");
    await user.type(screen.getByLabelText("재고 일괄 입력"), "5");
    await user.click(screen.getByRole("button", { name: "모두 적용" }));

    expect(hidden("skus")).toEqual([
      { optionValues: ["M"], price: 19_900, initialStock: 5 },
      { optionValues: ["L"], price: 19_900, initialStock: 5 },
    ]);
  });

  it("옵션 값이 비어 있으면 조합을 만들지 않고 알려 준다", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(screen.getByRole("button", { name: "옵션 추가" }));
    await user.type(screen.getByLabelText("옵션 1 이름"), "색상");
    await user.click(screen.getByRole("button", { name: "조합 만들기" }));

    expect(screen.getByText("옵션마다 값을 하나 이상 넣어 주세요.")).toBeInTheDocument();
  });
});
