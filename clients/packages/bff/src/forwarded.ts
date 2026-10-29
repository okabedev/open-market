/**
 * 요청의 `X-Forwarded-For`에서 브라우저 주소를 고른다. 앱 서버(`bin/serve.mjs`)가 브라우저가 보낸 값을 지우고 연결 주소를 넣는다.
 * Next.js는 이 헤더가 이미 있으면 그대로 두므로 `next start`·`next dev`로 띄우면 브라우저가 고른 값이 된다. gateway는 BFF가 보낸
 * 이 값으로 익명 요청의 레이트 리밋을 센다(ADR 0011).
 */
export function clientAddress(forwardedFor: string | null): string | undefined {
  const last = forwardedFor?.split(",").at(-1)?.trim();
  return last ? last : undefined;
}
