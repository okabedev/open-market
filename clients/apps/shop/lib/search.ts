/** 검색 정렬(docs/architecture/search.md "검색 API"). 어떤 정렬이든 품절 상품은 뒤로 간다. */
export const SORTS = {
  RELEVANCE: "관련도순",
  LATEST: "최신순",
  PRICE_ASC: "낮은 가격순",
  PRICE_DESC: "높은 가격순",
} as const;

export type Sort = keyof typeof SORTS;

/** 상품 검색 조건. URL 검색 조건(`/products?q=&categoryId=&minPrice=&maxPrice=&sort=&cursor=`)과 같다. */
export type SearchQuery = {
  q?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sort: Sort;
  cursor?: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_KEYWORD = 100;

function single(value: string | string[] | undefined): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function price(value: string | string[] | undefined): number | undefined {
  const text = single(value)?.trim();
  if (!text || !/^\d{1,9}$/.test(text)) {
    return undefined;
  }
  return Number(text);
}

/** URL 검색 조건을 읽는다. 빈 값과 틀린 값은 버린다(검색 API가 400을 주지 않게). */
export function searchQuery(params: Record<string, string | string[] | undefined>): SearchQuery {
  const q = single(params.q)?.trim().slice(0, MAX_KEYWORD);
  const categoryId = single(params.categoryId);
  const sort = single(params.sort);
  const cursor = single(params.cursor);
  const minPrice = price(params.minPrice);
  const maxPrice = price(params.maxPrice);
  return {
    ...(q ? { q } : {}),
    ...(categoryId && UUID.test(categoryId) ? { categoryId } : {}),
    ...(minPrice !== undefined ? { minPrice } : {}),
    ...(maxPrice !== undefined ? { maxPrice } : {}),
    sort: sort && sort in SORTS ? (sort as Sort) : "RELEVANCE",
    ...(cursor ? { cursor } : {}),
  };
}

/**
 * 검색 화면 주소. `changes`로 조건을 바꾸고(undefined면 뺀다), 커서를 주지 않으면 첫 쪽으로 간다. 기본 정렬(관련도)은 적지 않는다.
 */
export function searchHref(current: SearchQuery, changes: Partial<SearchQuery>): string {
  const next: Partial<SearchQuery> = { ...current, cursor: undefined, ...changes };
  const params = new URLSearchParams();
  for (const name of ["q", "categoryId", "minPrice", "maxPrice", "sort", "cursor"] as const) {
    const value = next[name];
    if (value === undefined || value === "" || (name === "sort" && value === "RELEVANCE")) {
      continue;
    }
    params.set(name, String(value));
  }
  const search = params.toString();
  return search ? `/products?${search}` : "/products";
}
