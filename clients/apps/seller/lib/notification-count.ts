import { bff } from "./bff";

/**
 * 안 읽은 알림 수(`GET /v1/seller/notifications/unread-count`). 머리글이 화면마다 읽으므로, notification에 닿지 못해도 화면이 깨지지 않게 0으로
 * 본다.
 */
export async function unreadCount(): Promise<number> {
  try {
    const notification = await bff.serviceClient("notification");
    const { data } = await notification.GET("/v1/seller/notifications/unread-count");
    return data?.count ?? 0;
  } catch {
    return 0;
  }
}
