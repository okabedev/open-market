import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  carrierLabel,
  claimLabel,
  claimReasonLabel,
  ClaimBadge,
  progressLabel,
  ProgressBadge,
  shipmentStatusLabel,
} from "./fulfillment-badges";

describe("progressLabel", () => {
  it('하위 주문 진행 상태의 화면 이름(docs/architecture/order.md "하위 주문 진행 상태")', () => {
    expect(progressLabel("PAID")).toBe("결제 완료");
    expect(progressLabel("ACCEPTED")).toBe("상품 준비 중");
    expect(progressLabel("SHIPPED")).toBe("발송");
    expect(progressLabel("IN_TRANSIT")).toBe("배송 중");
    expect(progressLabel("DELIVERED")).toBe("배송 완료");
    expect(progressLabel("CONFIRMED")).toBe("구매확정");
    expect(progressLabel("CANCELLED")).toBe("취소 완료");
    expect(progressLabel("RETURNED")).toBe("반품 완료");
    expect(progressLabel(undefined)).toBe("-");
  });
});

describe("shipmentStatusLabel", () => {
  it('배송 상태의 화면 이름(docs/glossary.md "배송")', () => {
    expect(shipmentStatusLabel("READY")).toBe("결제 완료");
    expect(shipmentStatusLabel("ACCEPTED")).toBe("상품 준비 중");
    expect(shipmentStatusLabel("CANCELLED")).toBe("취소됨");
  });
});

describe("claimLabel", () => {
  it("유형과 상태를 이어 붙인다", () => {
    expect(claimLabel({ type: "RETURN", status: "REQUESTED" })).toBe("반품 신청");
    expect(claimLabel({ type: "EXCHANGE", status: "MEDIATING" })).toBe("교환 중재 중");
    expect(claimLabel({ type: "CANCEL", status: "COMPLETED" })).toBe("취소 완료");
    expect(claimLabel(undefined)).toBe("");
  });
});

describe("이름표", () => {
  it("사유와 택배사", () => {
    expect(claimReasonLabel("NOT_AS_DESCRIBED")).toBe("상품 정보와 다름");
    expect(claimReasonLabel("BUYER_CANCEL")).toBe("발송 전 취소");
    expect(carrierLabel("CJ_LOGISTICS")).toBe("CJ대한통운");
    expect(carrierLabel("UNKNOWN")).toBe("-");
  });
});

describe("배지", () => {
  it("진행 상태와 클레임을 보여 주고, 클레임이 없으면 그리지 않는다", () => {
    const { container } = render(
      <>
        <ProgressBadge progress="IN_TRANSIT" />
        <ClaimBadge claim={{ type: "RETURN", status: "REJECTED" }} />
        <ClaimBadge claim={undefined} />
      </>,
    );
    expect(screen.getByText("배송 중")).toBeInTheDocument();
    expect(screen.getByText("반품 거부")).toBeInTheDocument();
    expect(container.querySelectorAll("span")).toHaveLength(2);
  });
});
