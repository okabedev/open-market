import { describe, expect, it } from "vitest";
import { notificationHref, safeAppPath } from "./notifications";

const ID = "0199f5a0-0000-7000-8000-0000000000a1";

describe("notificationHref", () => {
  it("이동 대상을 이 앱의 화면으로 바꾸고, 화면이 없으면 null이다", () => {
    expect(notificationHref({ targetType: "SELLER_ORDER", targetId: ID })).toBe(`/orders/${ID}`);
    expect(notificationHref({ targetType: "CLAIM", targetId: ID })).toBe("/orders?status=claims");
    expect(notificationHref({ targetType: "PRODUCT", targetId: ID })).toBe(`/products/${ID}`);
    expect(notificationHref({ targetType: "ORDER", targetId: ID })).toBeNull();
    expect(notificationHref({ targetType: "REVIEW", targetId: ID })).toBe(`/reviews#review-${ID}`);
    expect(notificationHref({ targetType: "SETTLEMENT", targetId: ID })).toBe(`/settlements/${ID}`);
  });
});

describe("safeAppPath", () => {
  it("이 앱 안의 경로만 믿는다", () => {
    expect(safeAppPath("/orders?status=claims")).toBe("/orders?status=claims");
    expect(safeAppPath("//evil.example")).toBe("/notifications");
    expect(safeAppPath("/\\evil.example")).toBe("/notifications");
    expect(safeAppPath("https://evil.example")).toBe("/notifications");
  });
});
