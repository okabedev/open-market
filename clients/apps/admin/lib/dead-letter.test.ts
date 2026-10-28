import { describe, expect, it } from "vitest";
import { deadLetterQuery, sameEventCounts, shortClassName } from "./dead-letter";
import { encodeCursor } from "./merged-page";

const CURSOR = encodeCursor("2026-10-28T01:00:00Z", "0199f000-0000-7000-8000-000000000009");

describe("deadLetterQuery", () => {
  it("조건이 없으면 모든 서비스의 처리 대기(PENDING)를 묻는다", () => {
    expect(deadLetterQuery({})).toEqual({
      status: "PENDING",
      services: expect.arrayContaining(["order", "payment", "auth"]),
      query: { status: "PENDING" },
      filters: {},
    });
  });

  it("상태·서비스·토픽·커서를 넘긴다. 서비스로 거르면 그 서비스에만 묻는다", () => {
    const result = deadLetterQuery({
      status: "DISCARDED",
      service: "payment",
      topic: " order.order ",
      before: CURSOR,
    });
    expect(result.status).toBe("DISCARDED");
    expect(result.services).toEqual(["payment"]);
    expect(result.query).toEqual({ status: "DISCARDED", topic: "order.order", before: CURSOR });
    expect(result.filters).toEqual({ service: "payment", topic: "order.order" });
  });

  it("전체(ALL)는 상태를 거르지 않는다. 같은 이벤트의 처리한 행과 새 행을 함께 본다", () => {
    const result = deadLetterQuery({ status: "ALL", topic: "review.review" });
    expect(result.status).toBe("ALL");
    expect(result.query).toEqual({ topic: "review.review" });
  });

  it("모르는 상태·서비스, 토픽 이름이 아닌 값, 틀린 커서는 무시한다", () => {
    const result = deadLetterQuery({
      status: "DELETED",
      service: "evil",
      topic: "a b;c",
      before: "abc",
    });
    expect(result.status).toBe("PENDING");
    expect(result.services.length).toBeGreaterThan(1);
    expect(result.query).toEqual({ status: "PENDING" });
    expect(result.filters).toEqual({});
  });
});

describe("sameEventCounts", () => {
  it("쪽 안에서 같은 이벤트 ID의 행 수를 센다(다시 보낸 뒤 또 실패하면 새 행이 생긴다)", () => {
    const counts = sameEventCounts([
      { item: { eventId: "e1" } },
      { item: { eventId: "e2" } },
      { item: { eventId: "e1" } },
      { item: {} },
    ]);
    expect(counts.get("e1")).toBe(2);
    expect(counts.get("e2")).toBe(1);
  });
});

describe("shortClassName", () => {
  it("패키지를 뗀 클래스 이름이다", () => {
    expect(
      shortClassName("org.springframework.kafka.listener.ListenerExecutionFailedException"),
    ).toBe("ListenerExecutionFailedException");
    expect(shortClassName(undefined)).toBe("-");
  });
});
