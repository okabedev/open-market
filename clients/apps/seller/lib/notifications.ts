import type { NotificationComponents } from "@openmarket/api-client";

type Target = Pick<
  NotificationComponents["schemas"]["NotificationView"],
  "targetType" | "targetId"
>;

/**
 * seller(판매자) 알림이 열 화면(docs/architecture/notification.md "인앱 알림"). 리뷰는 한 건을 읽는 API가 없어 리뷰 목록의 그
 * 리뷰로 간다(새 리뷰는 첫 쪽 맨 위다). 이 앱에 화면이 없는 대상은 null이고 읽음 표시만 한다.
 */
export function notificationHref({ targetType, targetId: id }: Target): string | null {
  switch (targetType) {
    case "SELLER_ORDER":
      return id ? `/orders/${id}` : null;
    case "CLAIM":
      // M5부터 클레임 신청 알림은 SELLER_ORDER로 온다. 그 전에 만든 알림은 클레임 ID만 있어 클레임 진행 중 목록으로 간다.
      return "/orders?status=claims";
    case "REVIEW":
      return id ? `/reviews#review-${id}` : null;
    case "SETTLEMENT":
      return id ? `/settlements/${id}` : null;
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
