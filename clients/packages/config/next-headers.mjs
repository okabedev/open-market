// 앱 3개가 모든 응답에 붙이는 보안 헤더(docs/architecture/auth.md "보안 기본값"). 앱 next.config.ts의 headers()가 쓴다.
// - 다른 사이트가 앱을 프레임에 넣지 못하게 한다(관리자 버튼 클릭재킹). CSP는 frame-ancestors만 둔다. 스크립트 출처 제한은 Next.js
//   인라인 스크립트에 nonce가 필요해 아직 하지 않는다(docs/architecture/security.md).
// - HSTS는 HTTPS로 서비스할 때 앞단에서 붙인다. 로컬은 HTTP다.
export const securityHeaders = [
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
];
