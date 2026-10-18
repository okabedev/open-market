/**
 * 요청의 `X-Forwarded-For`에서 브라우저 주소를 고른다. 마지막 값은 Next.js 서버가 연결 주소로 붙인 것이고, 그 앞은 브라우저가 마음대로 넣을
 * 수 있다. gateway는 BFF가 보낸 이 값으로 익명 요청의 레이트 리밋을 센다(ADR 0011).
 */
export function clientAddress(forwardedFor: string | null): string | undefined {
  const last = forwardedFor?.split(",").at(-1)?.trim();
  return last ? last : undefined;
}
