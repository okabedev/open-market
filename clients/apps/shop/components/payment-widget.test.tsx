// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { loadTossPayments } = vi.hoisted(() => ({ loadTossPayments: vi.fn() }));
vi.mock("@tosspayments/tosspayments-sdk", () => ({ ANONYMOUS: "@@ANONYMOUS", loadTossPayments }));

import { PaymentWidget } from "./payment-widget";

const props = {
  clientKey: "test_gck",
  orderId: "order-1",
  orderName: "반팔 티셔츠",
  amount: 39800,
};

describe("PaymentWidget", () => {
  beforeEach(() => {
    const widgets = {
      setAmount: vi.fn().mockResolvedValue(undefined),
      renderPaymentMethods: vi.fn().mockResolvedValue({}),
      renderAgreement: vi.fn().mockResolvedValue({}),
      requestPayment: vi.fn(),
    };
    loadTossPayments.mockResolvedValue({ widgets: () => widgets });
  });

  afterEach(() => {
    cleanup();
    loadTossPayments.mockReset();
  });

  it("SDK 주소가 없으면 토스 기본 스크립트를 불러온다", async () => {
    render(<PaymentWidget {...props} />);
    expect(await screen.findByRole("button", { name: "결제하기" })).toBeEnabled();
    expect(loadTossPayments).toHaveBeenCalledWith("test_gck", {});
  });

  it("SDK 주소가 있으면 그 스크립트를 불러온다(E2E의 가짜 PG, ADR 0018)", async () => {
    render(<PaymentWidget {...props} sdkUrl="http://localhost:8090/v2/standard" />);
    expect(await screen.findByRole("button", { name: "결제하기" })).toBeEnabled();
    expect(loadTossPayments).toHaveBeenCalledWith("test_gck", {
      src: "http://localhost:8090/v2/standard",
    });
  });
});
