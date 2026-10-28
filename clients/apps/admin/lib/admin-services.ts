/**
 * 관리자 조회 API(`/v1/admin/audit-logs`, `/v1/admin/dead-letters`)를 여는 서비스. admin BFF가 이 목록에 동시에 묻고 합친다
 * (ADR 0023). gateway에 `/api/<서비스>` 라우트가 있는 서비스는 모두 `common-web`·`common-messaging`으로 같은 API를 연다. 서비스를
 * 추가하면 여기에도 더한다(add-service skill).
 */
export const ADMIN_SERVICES = [
  "member",
  "catalog",
  "order",
  "payment",
  "fulfillment",
  "promotion",
  "review",
  "settlement",
  "search",
  "auth",
  "file",
  "notification",
] as const;

export type AdminService = (typeof ADMIN_SERVICES)[number];

export function adminService(value: unknown): AdminService | undefined {
  return ADMIN_SERVICES.find((service) => service === value);
}
