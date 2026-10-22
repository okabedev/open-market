import type { NotificationComponents } from "@openmarket/api-client";

type Target = Pick<
  NotificationComponents["schemas"]["NotificationView"],
  "targetType" | "targetId"
>;

/**
 * admin(관리자) 알림이 열 화면(docs/architecture/notification.md "인앱 알림"). 이 앱에 화면이 없는 대상은 null이고 읽음 표시만
 * 한다. 관리자 화면은 목록이라 대상 ID는 쓰지 않는다.
 */
export function notificationHref({ targetType }: Target): string | null {
  switch (targetType) {
    case "CLAIM":
      return "/claims";
    case "REFUND":
      return "/payment-failures";
    default:
      return null;
  }
}

/** 알림을 연 뒤 갈 경로. 폼 값이라 이 앱 안의 경로(`/`로 시작하고 `//`, `/\`가 아닌 것)만 믿는다. */
export function safeAppPath(value: string): string {
  return /^\/(?![/\\])/.test(value) ? value : "/notifications";
}
