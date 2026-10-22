import { describe, expect, it } from "vitest";
import { orderTabFilter } from "./order-tabs";

describe("orderTabFilter", () => {
  it("탭마다 진행 상태나 클레임 상태 조건을 만든다", () => {
    expect(orderTabFilter(undefined)).toEqual({});
    expect(orderTabFilter("to-accept")).toEqual({ progress: ["PAID"] });
    expect(orderTabFilter("in-delivery")).toEqual({ progress: ["SHIPPED", "IN_TRANSIT"] });
    expect(orderTabFilter("claims")).toEqual({
      claimStatus: ["REQUESTED", "APPROVED", "MEDIATING"],
    });
  });
});
