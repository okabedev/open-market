import createClient, { type Client } from "openapi-fetch";
import type { paths as MemberPaths } from "./generated/member";

/** 서비스마다 생성한 경로 타입. 서비스를 추가하면 openapi/<서비스>.json과 함께 여기에 더한다. */
type ServicePaths = {
  member: MemberPaths;
};

export type ServiceName = keyof ServicePaths;

export type ServiceClient<S extends ServiceName> = Client<ServicePaths[S]>;

export type ClientOptions = {
  /** gateway 주소. 없으면 환경 변수 `GATEWAY_URL`, 그것도 없으면 `http://localhost:8080` */
  gatewayUrl?: string;
  /** 테스트에서 요청을 가로챌 때 쓴다 */
  fetch?: (request: Request) => Promise<Response>;
};

const DEFAULT_GATEWAY_URL = "http://localhost:8080";

/**
 * 서비스 API 클라이언트를 만든다. gateway의 `/api/<서비스>` 아래 경로를 OpenAPI 타입대로 부른다.
 *
 * 성공(2xx)이면 `data`, 아니면 `error`(ProblemDetail 본문)가 채워진다. 예외는 네트워크 오류에서만 난다.
 * 사용자 토큰 전달은 BFF 세션과 함께 M1에서 붙인다.
 */
export function createServiceClient<S extends ServiceName>(
  service: S,
  options: ClientOptions = {},
): ServiceClient<S> {
  const gatewayUrl = options.gatewayUrl ?? process.env.GATEWAY_URL ?? DEFAULT_GATEWAY_URL;
  return createClient<ServicePaths[S]>({
    baseUrl: `${gatewayUrl.replace(/\/+$/, "")}/api/${service}`,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
}
