import type { NotificationComponents } from "@openmarket/api-client";

type Target = Pick<
  NotificationComponents["schemas"]["NotificationView"],
  "targetType" | "targetId"
>;

/**
 * shop(구매자) 알림이 열 화면(docs/architecture/notification.md "인앱 알림"). 이 앱에 화면이 없는 대상은 null이고 읽음 표시만
 * 한다.
 */
export function notificationHref({ targetType, targetId: id }: Target): string | null {
  switch (targetType) {
    case "ORDER":
      return id ? `/orders/${id}` : null;
    case "SELLER_APPLICATION":
      return "/my/seller";
    default:
      return null;
  }
}

/** 알림을 연 뒤 갈 경로. 폼 값이라 이 앱 안의 경로(`/`로 시작하고 `//`, `/\`가 아닌 것)만 믿는다. */
export function safeAppPath(value: string): string {
  return /^\/(?![/\\])/.test(value) ? value : "/notifications";
}
