import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { OrderStatusBadge, orderStatusLabel } from "./order-status-badge";

describe("orderStatusLabel", () => {
  it('주문 상태의 화면 이름(docs/glossary.md "주문")', () => {
    expect(orderStatusLabel("PENDING")).toBe("재고 확인 중");
    expect(orderStatusLabel("AWAITING_PAYMENT")).toBe("결제 대기");
    expect(orderStatusLabel("PAID")).toBe("결제 완료");
    expect(orderStatusLabel("CANCELLED")).toBe("취소됨");
  });

  it("모르는 상태는 -", () => {
    expect(orderStatusLabel("SHIPPED")).toBe("-");
    expect(orderStatusLabel(undefined)).toBe("-");
  });
});

describe("OrderStatusBadge", () => {
  it("상태 이름을 보여 준다", () => {
    render(<OrderStatusBadge status="PAID" />);
    expect(screen.getByText("결제 완료")).toBeInTheDocument();
  });
});
