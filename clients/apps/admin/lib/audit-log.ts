import { ADMIN_SERVICES, adminService, type AdminService } from "./admin-services";
import { addDays, kstStartOf, parseDate } from "./dates";

/**
 * 감사 로그 화면(ADM-4, ADR 0023)의 조건과 합치기. 서비스마다 `GET /v1/admin/audit-logs`가 `(시각, id)` 내림차순으로 커서보다 앞선 행을
 * `size`개까지 준다(docs/architecture/data.md "audit_log"). 화면은 모든 대상 서비스의 결과를 같은 순서로 합쳐 앞의 `size`개를 보이므로, 합친
 * 앞 `size`개는 전체에서도 맞다. 다음 쪽 커서는 보인 마지막 행으로 만들어 모든 서비스에 같이 보낸다.
 */

/** 서비스가 남기는 작업. 서비스가 새 작업을 남기면 여기에 이름을 더한다(모르는 작업은 코드 그대로 보인다). */
export const ACTION_LABELS: Record<string, string> = {
  SELLER_APPLICATION_APPROVED: "입점 승인",
  SELLER_APPLICATION_REJECTED: "입점 반려",
  SELLER_SUSPENDED: "판매자 정지",
  SELLER_REINSTATED: "판매자 정지 해제",
  PRODUCT_BANNED: "상품 제재",
  COUPON_CREATED: "플랫폼 쿠폰 생성",
  COUPON_STOPPED: "쿠폰 발급 중지",
  REVIEW_HIDDEN: "리뷰 숨김",
  REVIEW_SHOWN: "리뷰 다시 보이기",
  CLAIM_MEDIATION_APPROVED: "클레임 중재 승인",
  CLAIM_MEDIATION_REJECTED: "클레임 거부 확정",
  REFUND_RETRIED: "환불 재시도",
  PAYMENT_CANCEL_RETRIED: "결제 취소 재시도",
  COMMISSION_RATE_CHANGED: "수수료율 변경",
  SETTLEMENT_RUN_EXECUTED: "정산 작업 즉시 실행",
  SETTLEMENT_PAID_OUT: "정산 지급",
  DEAD_LETTER_RETRIED: "DLT 메시지 다시 보내기",
  DEAD_LETTER_DISCARDED: "DLT 메시지 버리기",
};

const TARGET_LABELS: Record<string, string> = {
  SELLER_APPLICATION: "입점 신청",
  SELLER: "판매자",
  PRODUCT: "상품",
  COUPON: "쿠폰",
  REVIEW: "리뷰",
  CLAIM: "클레임",
  PAYMENT: "결제",
  REFUND: "환불",
  CATEGORY: "카테고리",
  SETTLEMENT: "정산",
  SETTLEMENT_RUN: "정산 작업",
  DEAD_LETTER: "DLT 메시지",
};

export function actionLabel(action: string | undefined): string {
  return action ? (ACTION_LABELS[action] ?? action) : "-";
}

export function targetLabel(targetType: string | undefined): string {
  return targetType ? (TARGET_LABELS[targetType] ?? targetType) : "-";
}

type Params = Record<string, string | string[] | undefined>;

export type AuditLogFilters = {
  from?: string;
  to?: string;
  service?: AdminService;
  action?: string;
  actorId?: string;
};

export type AuditLogQuery = {
  /** 물을 서비스. 서비스로 거르면 그 서비스 하나다 */
  services: readonly AdminService[];
  /** 서비스 API 조건 */
  query: { from?: string; to?: string; action?: string; actorId?: string; before?: string };
  /** 화면 조건(다음 쪽 링크가 이어 쓴다) */
  filters: AuditLogFilters;
  error: string | null;
};

const single = (value: string | string[] | undefined) =>
  typeof value === "string" && value.trim() !== "" ? value.trim() : undefined;

/** 검색 조건을 서비스 조건으로. 날짜는 KST로 시작일 00:00 이상, 종료일 다음 날 00:00 미만이다. */
export function auditLogQuery(params: Params): AuditLogQuery {
  const from = single(params.from);
  const to = single(params.to);
  const service = adminService(single(params.service));
  const action = single(params.action);
  const actorId = single(params.actorId)?.slice(0, 100);
  const before = single(params.before);
  const filters: AuditLogFilters = {
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(service ? { service } : {}),
    ...(action && action in ACTION_LABELS ? { action } : {}),
    ...(actorId ? { actorId } : {}),
  };
  const result: AuditLogQuery = {
    services: service ? [service] : ADMIN_SERVICES,
    query: {
      ...(filters.action ? { action: filters.action } : {}),
      ...(actorId ? { actorId } : {}),
      // 형식이 틀린 커서는 모든 서비스가 400이라 첫 쪽으로 돌린다.
      ...(before && decodeCursor(before) !== null ? { before } : {}),
    },
    filters,
    error: null,
  };
  const fromTime = from === undefined ? undefined : parseDate(from);
  const toTime = to === undefined ? undefined : parseDate(to);
  if (fromTime === null || toTime === null) {
    return { ...result, error: "날짜를 yyyy-MM-dd 형식의 있는 날짜로 입력해 주세요." };
  }
  if (fromTime !== undefined && toTime !== undefined && fromTime > toTime) {
    return { ...result, error: "시작일이 종료일보다 늦습니다." };
  }
  if (from) {
    result.query.from = kstStartOf(from);
  }
  if (to) {
    result.query.to = kstStartOf(addDays(to, 1));
  }
  return result;
}

const CURSOR = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z,[0-9a-f-]{36}$/;

/** 서비스와 같은 커서: `<시각(ISO-8601)>,<id>`를 base64url(패딩 없음)로. */
export function encodeCursor(occurredAt: string, id: string): string {
  return Buffer.from(`${occurredAt},${id}`, "utf8").toString("base64url");
}

/** 커서 안의 `<시각>,<id>`. 형식이 틀리면 null이다. */
export function decodeCursor(cursor: string): string | null {
  if (!/^[A-Za-z0-9_-]+$/.test(cursor)) {
    return null;
  }
  const value = Buffer.from(cursor, "base64url").toString("utf8");
  return CURSOR.test(value) ? value : null;
}

/** ISO 시각(UTC, `Z`)의 소수 자릿수를 9자리로 맞춘 비교 키. 서비스는 끝의 0을 떼고 쓴다. */
function instantKey(iso: string): string {
  const match = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d+))?Z$/.exec(iso);
  return match ? `${match[1]}.${(match[2] ?? "").padEnd(9, "0")}` : iso;
}

export type AuditLogRow<I> = { service: string; item: I };
type Item = { id?: string; occurredAt?: string };

/**
 * 서비스 결과를 `(시각, id)` 내림차순으로 합쳐 앞의 `size`개를 준다. 합친 행이 `size`를 넘거나 다음 쪽이 있는 서비스가 있으면 보인 마지막
 * 행으로 커서를 만든다.
 */
export function mergeAuditLogs<I extends Item>(
  pages: AuditLogRow<I>[][],
  hasMore: boolean[],
  size: number,
): { rows: AuditLogRow<I>[]; nextCursor: string | null } {
  const key = (row: AuditLogRow<I>) => instantKey(row.item.occurredAt ?? "");
  const merged = pages.flat().sort((a, b) => {
    const byTime = key(b).localeCompare(key(a));
    return byTime !== 0 ? byTime : (b.item.id ?? "").localeCompare(a.item.id ?? "");
  });
  const rows = merged.slice(0, size);
  const last = rows.at(-1);
  const more = merged.length > size || hasMore.some(Boolean);
  return {
    rows,
    nextCursor: more && last ? encodeCursor(last.item.occurredAt ?? "", last.item.id ?? "") : null,
  };
}
