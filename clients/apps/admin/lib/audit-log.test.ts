import { describe, expect, it } from "vitest";
import {
  actionLabel,
  auditLogQuery,
  decodeCursor,
  encodeCursor,
  mergeAuditLogs,
} from "./audit-log";

const CURSOR = encodeCursor("2026-10-28T01:00:00.123Z", "0199f000-0000-7000-8000-000000000009");

const row = (service: string, occurredAt: string, id: string) => ({
  service,
  item: { id, occurredAt, action: "COUPON_STOPPED", actorId: "admin" },
});

describe("auditLogQuery", () => {
  it("조건이 없으면 모든 서비스에 기간 없이 묻는다", () => {
    expect(auditLogQuery({})).toEqual({
      services: expect.arrayContaining(["member", "settlement", "auth"]),
      query: {},
      filters: {},
      error: null,
    });
  });

  it("날짜(KST)를 시작일 00:00 이상, 종료일 다음 날 00:00 미만 ISO 시각으로 바꾼다", () => {
    expect(auditLogQuery({ from: "2026-10-01", to: "2026-10-02" }).query).toEqual({
      from: "2026-09-30T15:00:00.000Z",
      to: "2026-10-02T15:00:00.000Z",
    });
  });

  it("서비스로 거르면 그 서비스에만 묻고, 작업·작업한 사람·커서를 넘긴다", () => {
    const result = auditLogQuery({
      service: "member",
      action: "SELLER_SUSPENDED",
      actorId: " 0199f000-0000-7000-8000-000000000001 ",
      before: CURSOR,
    });
    expect(result.services).toEqual(["member"]);
    expect(result.query).toEqual({
      action: "SELLER_SUSPENDED",
      actorId: "0199f000-0000-7000-8000-000000000001",
      before: CURSOR,
    });
    expect(result.filters).toEqual({
      service: "member",
      action: "SELLER_SUSPENDED",
      actorId: "0199f000-0000-7000-8000-000000000001",
    });
  });

  it("모르는 서비스·작업은 무시하고, 날짜 형식이 틀리거나 거꾸로면 오류다", () => {
    expect(auditLogQuery({ service: "evil", action: "DROP" }).services.length).toBeGreaterThan(1);
    expect(auditLogQuery({ service: "evil", action: "DROP" }).query).toEqual({});
    expect(auditLogQuery({ before: "abc" }).query).toEqual({});
    expect(auditLogQuery({ from: "2026-13-01" }).error).toContain("날짜");
    expect(auditLogQuery({ from: "2026-10-02", to: "2026-10-01" }).error).toContain("늦습니다");
  });
});

describe("mergeAuditLogs", () => {
  it("서비스 결과를 시각·ID 내림차순으로 합쳐 앞의 size개를 주고, 마지막 행으로 커서를 만든다", () => {
    const page = mergeAuditLogs(
      [
        [
          row("member", "2026-10-28T03:00:00.5Z", "0199-a"),
          row("member", "2026-10-28T01:00:00Z", "0199-b"),
        ],
        [
          row("payment", "2026-10-28T03:00:00.123456Z", "0199-c"),
          row("payment", "2026-10-28T03:00:00.5Z", "0199-d"),
        ],
      ],
      [false, false],
      3,
    );
    expect(page.rows.map((r) => r.item.id)).toEqual(["0199-d", "0199-a", "0199-c"]);
    expect(page.nextCursor).toBe(encodeCursor("2026-10-28T03:00:00.123456Z", "0199-c"));
  });

  it("합친 행이 size 이하이고 다음 쪽이 있는 서비스가 없으면 커서가 없다", () => {
    const page = mergeAuditLogs([[row("member", "2026-10-28T01:00:00Z", "0199-b")]], [false], 3);
    expect(page.nextCursor).toBeNull();
  });

  it("한 서비스라도 다음 쪽이 있으면 합친 행이 적어도 커서를 만든다", () => {
    const id = "0199f000-0000-7000-8000-00000000000b";
    const page = mergeAuditLogs([[row("member", "2026-10-28T01:00:00Z", id)]], [true], 3);
    expect(decodeCursor(page.nextCursor ?? "")).toBe(`2026-10-28T01:00:00Z,${id}`);
  });
});

describe("actionLabel", () => {
  it("알려진 작업은 한국어로, 모르는 작업은 그대로 보인다", () => {
    expect(actionLabel("SELLER_APPLICATION_APPROVED")).toBe("입점 승인");
    expect(actionLabel("NEW_ACTION")).toBe("NEW_ACTION");
  });
});
