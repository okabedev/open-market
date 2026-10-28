import { describe, expect, it } from "vitest";
import { decodeCursor, encodeCursor, mergeByTime } from "./merged-page";

const row = (service: string, occurredAt: string, id: string) => ({
  service,
  item: { id, occurredAt },
});
const mergeAuditLogs = (pages: ReturnType<typeof row>[][], hasMore: boolean[], size: number) =>
  mergeByTime(pages, hasMore, size, (item) => item.occurredAt);

describe("mergeByTime", () => {
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

describe("decodeCursor", () => {
  it("base64url 안의 `<시각>,<id>`만 받는다", () => {
    expect(decodeCursor("abc")).toBeNull();
    expect(decodeCursor("../x")).toBeNull();
    expect(decodeCursor(encodeCursor("2026-10-28T01:00:00Z", "not-a-uuid"))).toBeNull();
  });
});
