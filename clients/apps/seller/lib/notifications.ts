import type { NotificationComponents } from "@openmarket/api-client";

type Target = Pick<
  NotificationComponents["schemas"]["NotificationView"],
  "targetType" | "targetId"
>;

/**
 * seller(판매자) 알림이 열 화면(docs/architecture/notification.md "인앱 알림"). 이 앱에 화면이 없는 대상은 null이고 읽음 표시만
 * 한다.
 */
export function notificationHref({ targetType, targetId: id }: Target): string | null {
  switch (targetType) {
    case "SELLER_ORDER":
      return id ? `/orders/${id}` : null;
    case "CLAIM":
      // 알림은 클레임 ID만 갖는다. 클레임은 하위 주문 상세에서 처리하므로 클레임 진행 중 목록으로 간다.
      return "/orders?status=claims";
    case "PRODUCT":
      return id ? `/products/${id}` : null;
    default:
      return null;
  }
}

/** 알림을 연 뒤 갈 경로. 폼 값이라 이 앱 안의 경로(`/`로 시작하고 `//`, `/\`가 아닌 것)만 믿는다. */
export function safeAppPath(value: string): string {
  return /^\/(?![/\\])/.test(value) ? value : "/notifications";
}
