import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
const { noticeMessage, returnPath } = await import("./review");

describe("returnPath", () => {
  it("같은 목록의 조건을 지키고 처리 결과를 붙인다", () => {
    expect(returnPath("/sellers?status=SUSPENDED&page=2", "/sellers", "reinstated")).toBe(
      "/sellers?status=SUSPENDED&page=2&notice=reinstated",
    );
  });

  it("다른 경로나 외부 주소로는 보내지 않는다", () => {
    expect(returnPath("https://evil.example/sellers", "/sellers", "suspended")).toBe(
      "/sellers?notice=suspended",
    );
    expect(returnPath("/sellersX", "/sellers", "suspended")).toBe("/sellers?notice=suspended");
    expect(returnPath(null, "/sellers", "suspended")).toBe("/sellers?notice=suspended");
  });
});

describe("noticeMessage", () => {
  it("알려진 결과만 문구로 바꾼다", () => {
    expect(noticeMessage("approved")).toContain("승인했습니다");
    expect(noticeMessage("<script>")).toBeNull();
  });
});
