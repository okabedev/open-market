import { describe, expect, it } from "vitest";
import { buyerActions, canRequestMediation, reasonsFor } from "./claim";

describe("reasonsFor", () => {
  it("교환은 판매자 귀책 사유만 고른다", () => {
    expect(reasonsFor("RETURN")).toContain("CHANGE_OF_MIND");
    expect(reasonsFor("EXCHANGE")).toEqual(["DEFECTIVE", "WRONG_ITEM", "NOT_AS_DESCRIBED"]);
  });
});

describe("buyerActions", () => {
  const none = { cancel: false, confirm: false, claim: false };

  it("발송 전이면 취소만 한다", () => {
    expect(buyerActions({ shipments: [{ status: "READY" }] })).toEqual({ ...none, cancel: true });
    expect(buyerActions({ shipments: [{ status: "ACCEPTED" }] })).toEqual({
      ...none,
      cancel: true,
    });
  });

  it("배송 완료이고 확정 전이며 진행 중인 클레임이 없으면 구매확정과 클레임 신청을 한다", () => {
    expect(buyerActions({ shipments: [{ status: "DELIVERED" }] })).toEqual({
      ...none,
      confirm: true,
      claim: true,
    });
    expect(
      buyerActions({ shipments: [{ status: "DELIVERED" }], claims: [{ status: "REJECTED" }] }),
    ).toEqual({ ...none, confirm: true, claim: true });
  });

  it("확정했거나, 진행 중인 클레임이 있거나, 배송 중이면 할 일이 없다", () => {
    expect(
      buyerActions({ shipments: [{ status: "DELIVERED" }], confirmedAt: "2026-10-22T00:00:00Z" }),
    ).toEqual(none);
    expect(
      buyerActions({ shipments: [{ status: "DELIVERED" }], claims: [{ status: "MEDIATING" }] }),
    ).toEqual(none);
    expect(buyerActions({ shipments: [{ status: "IN_TRANSIT" }] })).toEqual(none);
  });

  it("교환 재발송은 발송 전이어도 취소하지 않는다", () => {
    expect(
      buyerActions({
        shipments: [{ status: "DELIVERED" }, { status: "READY" }],
        claims: [{ status: "APPROVED" }],
      }),
    ).toEqual(none);
  });
});

describe("canRequestMediation", () => {
  const rejected = { status: "REJECTED" };

  it("판매자가 거부했고 중재를 요청한 적이 없으면 요청한다", () => {
    expect(canRequestMediation({ claims: [rejected] }, rejected)).toBe(true);
  });

  it("중재를 거쳐 거부가 확정됐거나, 확정했거나, 다른 클레임이 진행 중이면 요청하지 않는다", () => {
    const mediated = { status: "REJECTED", mediationRequestedAt: "2026-10-22T00:00:00Z" };
    expect(canRequestMediation({ claims: [mediated] }, mediated)).toBe(false);
    expect(
      canRequestMediation({ claims: [rejected], confirmedAt: "2026-10-22T00:00:00Z" }, rejected),
    ).toBe(false);
    expect(canRequestMediation({ claims: [rejected, { status: "REQUESTED" }] }, rejected)).toBe(
      false,
    );
  });
});
