import { describe, expect, it } from "vitest";
import { listQuery } from "./list";

const STATUSES = ["SUBMITTED", "APPROVED"] as const;

describe("listQuery", () => {
  it("알려진 상태와 0 이상의 쪽 번호만 받는다", () => {
    expect(listQuery({ status: "APPROVED", page: "2" }, STATUSES, "SUBMITTED")).toEqual({
      status: "APPROVED",
      page: 2,
    });
    expect(listQuery({ status: "DROP TABLE", page: "-1" }, STATUSES, "SUBMITTED")).toEqual({
      status: "SUBMITTED",
      page: 0,
    });
    expect(listQuery({}, STATUSES, undefined)).toEqual({ status: undefined, page: 0 });
  });
});
