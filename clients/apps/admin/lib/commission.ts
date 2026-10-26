/** 수수료율 상한(bp, 0.01%). 원본은 settlement 규칙이다(docs/architecture/settlement.md "수수료율"). */
export const MAX_RATE_BP = 10_000;

/** 정수 부분과 소수 둘째 자리까지의 % 값. 지수 표기나 부호는 받지 않는다. */
const PERCENT = /^(\d{1,3})(?:\.(\d{1,2}))?$/;

/** 화면에서 받은 %(예: "8.5")를 bp(850)로 바꾼다. 0~100%, 소수 둘째 자리까지가 아니면 null이다. 부동소수 오차 없이 문자열로 계산한다. */
export function percentToBp(value: string): number | null {
  const match = PERCENT.exec(value.trim());
  if (!match) {
    return null;
  }
  const bp = Number(match[1]) * 100 + Number((match[2] ?? "").padEnd(2, "0"));
  return bp <= MAX_RATE_BP ? bp : null;
}

/** bp를 % 문구로 쓴다. 예: 850 → "8.5%". */
export function bpToPercent(bp: number): string {
  return `${(bp / 100).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}%`;
}
