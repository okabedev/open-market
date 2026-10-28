/**
 * 서비스마다 묻고 합치는 커서 목록(감사 로그, DLT 메시지, ADR 0023). 서비스는 `(시각, id)` 내림차순으로 커서보다 앞선 행을 `size`개까지
 * 준다. 모든 대상 서비스의 결과를 같은 순서로 합쳐 앞의 `size`개를 보이므로 합친 앞 `size`개는 전체에서도 맞다. 다음 쪽 커서는 보인 마지막
 * 행으로 만들어 모든 서비스에 같이 보낸다.
 */

const CURSOR = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z,[0-9a-f-]{36}$/;

/** 서비스와 같은 커서: `<시각(ISO-8601)>,<id>`를 base64url(패딩 없음)로. */
export function encodeCursor(at: string, id: string): string {
  return Buffer.from(`${at},${id}`, "utf8").toString("base64url");
}

/** 커서 안의 `<시각>,<id>`. 형식이 틀리면 null이다. 틀린 커서는 모든 서비스가 400이라 화면이 먼저 거른다. */
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

export type ServiceRow<I> = { service: string; item: I };

/**
 * 서비스 결과를 `(시각, id)` 내림차순으로 합쳐 앞의 `size`개를 준다. 합친 행이 `size`를 넘거나 다음 쪽이 있는 서비스가 있으면 보인 마지막
 * 행으로 커서를 만든다.
 */
export function mergeByTime<I extends { id?: string }>(
  pages: ServiceRow<I>[][],
  hasMore: boolean[],
  size: number,
  timeOf: (item: I) => string | undefined,
): { rows: ServiceRow<I>[]; nextCursor: string | null } {
  const key = (row: ServiceRow<I>) => instantKey(timeOf(row.item) ?? "");
  const merged = pages.flat().sort((a, b) => {
    const byTime = key(b).localeCompare(key(a));
    return byTime !== 0 ? byTime : (b.item.id ?? "").localeCompare(a.item.id ?? "");
  });
  const rows = merged.slice(0, size);
  const last = rows.at(-1);
  const more = merged.length > size || hasMore.some(Boolean);
  return {
    rows,
    nextCursor: more && last ? encodeCursor(timeOf(last.item) ?? "", last.item.id ?? "") : null,
  };
}
