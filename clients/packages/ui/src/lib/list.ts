/** 쪽 번호 목록(관리자, 판매자 관리 화면)의 쪽 번호와 상태 필터를 검색 조건에서 읽는다(서비스는 page 0부터, size 1~100). */
export function listQuery<S extends string>(
  params: Record<string, string | string[] | undefined>,
  statuses: readonly S[],
  fallback: S | undefined,
): { status: S | undefined; page: number } {
  const status = typeof params.status === "string" ? params.status : undefined;
  const page = Number.parseInt(typeof params.page === "string" ? params.page : "0", 10);
  return {
    status: statuses.find((candidate) => candidate === status) ?? fallback,
    page: Number.isFinite(page) && page >= 0 ? page : 0,
  };
}

export const PAGE_SIZE = 20;
