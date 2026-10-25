/** 자동완성 검색어 길이 상한. search `GET /v1/suggestions`의 `q`와 같다(docs/architecture/search.md "자동완성 API"). */
const MAX_KEYWORD = 50;

/** 입력을 멈추고 자동완성을 부르기까지 기다리는 시간(ms). */
export const SUGGESTION_DEBOUNCE_MS = 300;

/** 자동완성에 보낼 검색어. 앞뒤 공백을 지우고, 비었거나 50자를 넘으면 null(부르지 않는다). */
export function suggestionKeyword(value: string | null): string | null {
  const keyword = value?.trim() ?? "";
  return keyword.length === 0 || keyword.length > MAX_KEYWORD ? null : keyword;
}
