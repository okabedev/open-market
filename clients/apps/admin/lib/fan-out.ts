/** 서비스 하나를 기다리는 시간. 넘으면 그 서비스를 빼고 나머지로 보인다(NFR-4). */
export const FAN_OUT_TIMEOUT_MS = 5_000;

export type FanOutResult<S extends string, T> = {
  results: { service: S; value: T }[];
  /** 오류, 실패 응답(null), 시간 초과로 결과가 없는 서비스. 화면이 알린다 */
  failed: S[];
};

/**
 * 서비스마다 동시에 묻는다(ADR 0023). `call`이 null을 돌려주거나 던지거나 제한 시간을 넘긴 서비스는 `failed`로 모으고, 한 서비스 때문에
 * 화면 전체가 실패하지 않게 한다. `signal`은 제한 시간에 끊긴다. 요청에 넘기면 늦은 요청도 끊는다.
 */
export async function fanOut<S extends string, T>(
  services: readonly S[],
  call: (service: S, signal: AbortSignal) => Promise<T | null>,
  timeoutMs: number = FAN_OUT_TIMEOUT_MS,
): Promise<FanOutResult<S, T>> {
  const settled = await Promise.allSettled(
    services.map((service) => {
      const signal = AbortSignal.timeout(timeoutMs);
      // call이 signal을 쓰지 않아도 제한 시간에 끝낸다.
      const expired = new Promise<never>((_, reject) =>
        signal.addEventListener("abort", () => reject(signal.reason), { once: true }),
      );
      return Promise.race([call(service, signal), expired]);
    }),
  );
  const result: FanOutResult<S, T> = { results: [], failed: [] };
  settled.forEach((outcome, index) => {
    const service = services[index] as S;
    if (outcome.status === "fulfilled" && outcome.value !== null) {
      result.results.push({ service, value: outcome.value });
    } else {
      result.failed.push(service);
    }
  });
  return result;
}
