import type { AdminService } from "./admin-services";
import { bff } from "./bff";

/**
 * 공통 관리자 조회 API(감사 로그, DLT 메시지)를 부르는 클라이언트. 이 API는 `common-web`·`common-messaging`이 모든 서비스에 같은 모양으로
 * 열어서(ADR 0023) 한 서비스의 생성 타입으로 부른다. 서비스마다 다른 경로는 이 클라이언트로 부르지 않는다.
 */
export async function adminClient(service: AdminService) {
  return (await bff.serviceClient(service)) as unknown as Awaited<
    ReturnType<typeof bff.serviceClient<"member">>
  >;
}
