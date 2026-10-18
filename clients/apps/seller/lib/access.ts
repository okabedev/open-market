/** 판매자 센터에 들어올 수 있는지. 토큰의 역할은 승인·정지 뒤 다음 토큰 갱신(최대 10분)부터 바뀐다(auth.md "토큰"). */
export function isSeller(roles: readonly string[] | undefined): boolean {
  return roles?.includes("SELLER") ?? false;
}
