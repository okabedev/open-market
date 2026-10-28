import { ADMIN_SERVICES, adminService, type AdminService } from "./admin-services";
import { decodeCursor } from "./merged-page";

/**
 * DLT 메시지 화면(ADM-5, ADR 0022·0023)의 조건. 서비스마다 `GET /v1/admin/dead-letters`가 쌓인 시각 내림차순 커서 목록을 준다
 * (docs/architecture/messaging.md "DLT 재처리"). 화면은 결과를 `merged-page.ts`로 합친다. 페이로드와 예외 메시지는 응답에 없다.
 */

export const DEAD_LETTER_STATUSES = ["PENDING", "RETRIED", "DISCARDED"] as const;
export type DeadLetterStatus = (typeof DEAD_LETTER_STATUSES)[number];

/** 화면의 상태 탭. 전체(ALL)는 상태를 거르지 않아 같은 이벤트의 처리한 행과 새 행을 함께 본다. */
export type DeadLetterTab = DeadLetterStatus | "ALL";
export const DEAD_LETTER_TABS: readonly DeadLetterTab[] = [...DEAD_LETTER_STATUSES, "ALL"];

export const DEAD_LETTER_STATUS_LABELS: Record<DeadLetterTab, string> = {
  PENDING: "처리 대기",
  RETRIED: "다시 보냄",
  DISCARDED: "버림",
  ALL: "전체",
};

/** Kafka 토픽 이름에 쓸 수 있는 글자. 다른 값은 거르기에서 뺀다. */
const TOPIC = /^[A-Za-z0-9._-]{1,249}$/;

export type DeadLetterFilters = { service?: AdminService; topic?: string };

export type DeadLetterQuery = {
  status: DeadLetterTab;
  /** 물을 서비스. 서비스로 거르면 그 서비스 하나다 */
  services: readonly AdminService[];
  /** 서비스 API 조건 */
  query: { status?: DeadLetterStatus; topic?: string; before?: string };
  /** 상태 외의 화면 조건(탭과 다음 쪽 링크가 이어 쓴다) */
  filters: DeadLetterFilters;
};

const single = (value: string | string[] | undefined) =>
  typeof value === "string" && value.trim() !== "" ? value.trim() : undefined;

/** 검색 조건을 서비스 조건으로. 상태는 기본이 처리 대기(PENDING)이고 모르는 값은 무시한다. */
export function deadLetterQuery(
  params: Record<string, string | string[] | undefined>,
): DeadLetterQuery {
  const status = DEAD_LETTER_TABS.find((value) => value === params.status) ?? "PENDING";
  const service = adminService(single(params.service));
  const topicParam = single(params.topic);
  const topic = topicParam && TOPIC.test(topicParam) ? topicParam : undefined;
  const before = single(params.before);
  return {
    status,
    services: service ? [service] : ADMIN_SERVICES,
    query: {
      ...(status === "ALL" ? {} : { status }),
      ...(topic ? { topic } : {}),
      // 형식이 틀린 커서는 모든 서비스가 400이라 첫 쪽으로 돌린다.
      ...(before && decodeCursor(before) !== null ? { before } : {}),
    },
    filters: { ...(service ? { service } : {}), ...(topic ? { topic } : {}) },
  };
}

/** 쪽 안에서 이벤트 ID마다 행 수. 다시 보낸 메시지가 또 실패하면 같은 이벤트 ID로 새 행이 생긴다. */
export function sameEventCounts(rows: { item: { eventId?: string } }[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const { item } of rows) {
    if (item.eventId) {
      counts.set(item.eventId, (counts.get(item.eventId) ?? 0) + 1);
    }
  }
  return counts;
}

/** 패키지를 뗀 클래스 이름. 전체 이름은 화면의 title로 보인다. */
export function shortClassName(className: string | undefined): string {
  return className ? (className.split(".").at(-1) ?? className) : "-";
}
