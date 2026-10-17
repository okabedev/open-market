// 서버(서버 컴포넌트, Server Action, Route Handler)에서만 쓴다. 클라이언트 컴포넌트에서 불러오면 빌드가 실패한다.
import "server-only";

export {
  createServiceClient,
  type ClientOptions,
  type ServiceClient,
  type ServiceName,
} from "./client";
export type { components as MemberComponents, paths as MemberPaths } from "./generated/member";
