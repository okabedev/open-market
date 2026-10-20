import createClient, { type Client } from "openapi-fetch";
import type { paths as AuthPaths } from "./generated/auth";
import type { paths as CatalogPaths } from "./generated/catalog";
import type { paths as FilePaths } from "./generated/file";
import type { paths as MemberPaths } from "./generated/member";
import type { paths as OrderPaths } from "./generated/order";
import type { paths as SearchPaths } from "./generated/search";

/** 서비스마다 생성한 경로 타입. 서비스를 추가하면 openapi/<서비스>.json과 함께 여기에 더한다. */
type ServicePaths = {
  auth: AuthPaths;
  catalog: CatalogPaths;
  file: FilePaths;
  member: MemberPaths;
  order: OrderPaths;
  search: SearchPaths;
};

export type ServiceName = keyof ServicePaths;

export type ServiceClient<S extends ServiceName> = Client<ServicePaths[S]>;

export type ClientOptions = {
  /** gateway 주소. 없으면 환경 변수 `GATEWAY_URL`, 그것도 없으면 `http://localhost:8080` */
  gatewayUrl?: string;
  /** 사용자 액세스 토큰. BFF 세션에서 받는다(`@openmarket/bff`). 없으면 공개 경로만 부를 수 있다 */
  accessToken?: string;
  /**
   * 브라우저 주소. `X-Forwarded-For`로 붙인다. gateway가 익명 요청(가입 등)의 레이트 리밋을 이 값으로 센다(ADR 0011).
   * 없으면 모든 요청이 BFF 주소 하나로 세어진다
   */
  forwardedFor?: string;
  /** 테스트에서 요청을 가로챌 때 쓴다 */
  fetch?: (request: Request) => Promise<Response>;
};

const DEFAULT_GATEWAY_URL = "http://localhost:8080";

/**
 * 서비스 API 클라이언트를 만든다. gateway의 `/api/<서비스>` 아래 경로를 OpenAPI 타입대로 부른다.
 *
 * 성공(2xx)이면 `data`, 아니면 `error`(ProblemDetail 본문)가 채워진다. 예외는 네트워크 오류에서만 난다.
 * 사용자 API는 `accessToken`을 주거나 `@openmarket/bff`의 `serviceClient`를 쓴다.
 */
export function createServiceClient<S extends ServiceName>(
  service: S,
  options: ClientOptions = {},
): ServiceClient<S> {
  const gatewayUrl = options.gatewayUrl ?? process.env.GATEWAY_URL ?? DEFAULT_GATEWAY_URL;
  const headers: Record<string, string> = {};
  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }
  if (options.forwardedFor) {
    headers["X-Forwarded-For"] = options.forwardedFor;
  }
  return createClient<ServicePaths[S]>({
    baseUrl: `${gatewayUrl.replace(/\/+$/, "")}/api/${service}`,
    headers,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
}
