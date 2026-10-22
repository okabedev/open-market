import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ClaimCard } from "./claim-card";

describe("ClaimCard", () => {
  it("사유·귀책·처리 결과와 사진을 BFF 경로로 보여 준다", () => {
    render(
      <ClaimCard
        claim={{
          type: "RETURN",
          status: "COMPLETED",
          reason: "CHANGE_OF_MIND",
          fault: "BUYER",
          description: "사이즈가 맞지 않아요",
          fileIds: ["0199f5a0-0000-7000-8000-0000000000f1"],
          restock: true,
          refundAmount: 13_900,
        }}
        imageBase="/api/admin/claim-images"
      >
        <button type="button">동작</button>
      </ClaimCard>,
    );

    expect(screen.getByText("반품 완료")).toBeInTheDocument();
    expect(screen.getByText("단순 변심 (귀책 구매자)")).toBeInTheDocument();
    expect(screen.getByText("13,900원")).toBeInTheDocument();
    expect(screen.getByText("한다")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "클레임 사진 1" })).toHaveAttribute(
      "src",
      "/api/admin/claim-images/0199f5a0-0000-7000-8000-0000000000f1/w300",
    );
    expect(screen.getByRole("button", { name: "동작" })).toBeInTheDocument();
  });

  it("거부와 중재 결과를 보여 준다", () => {
    render(
      <ClaimCard
        claim={{
          type: "EXCHANGE",
          status: "REJECTED",
          reason: "DEFECTIVE",
          fault: "SELLER",
          rejectionReason: "검수 결과 이상 없음",
          mediationReason: "사진으로 불량을 확인할 수 없습니다",
        }}
      />,
    );

    expect(screen.getByText("교환 거부")).toBeInTheDocument();
    expect(screen.getByText(/검수 결과 이상 없음/)).toBeInTheDocument();
    expect(screen.getByText("거부 확정. 사진으로 불량을 확인할 수 없습니다")).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "클레임 사진" })).not.toBeInTheDocument();
  });
});
