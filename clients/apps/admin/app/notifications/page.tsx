import { Alert, NotificationList, PageHeader } from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { notificationHref } from "@/lib/notifications";
import { requireSession } from "@/lib/session";
import { openNotification, readAllNotifications } from "./actions";

/** 알림함(NTF-1). 최근 순 커서 목록이고, 알림을 열면 읽음이 되고 그 화면으로 간다. */
export default async function NotificationsPage({ searchParams }: PageProps<"/notifications">) {
  await requireSession("/notifications");
  const { cursor } = await searchParams;
  const notification = await bff.serviceClient("notification");
  const { data, response } = await notification.GET("/v1/admin/notifications", {
    params: { query: { ...(typeof cursor === "string" ? { cursor } : {}), size: 20 } },
  });
  return (
    <>
      <PageHeader title="알림" description="최근 순입니다. 90일이 지난 알림은 지워집니다." />
      {response.status === 400 ? (
        <Alert>
          목록 위치가 올바르지 않습니다.{" "}
          <Link href="/notifications" className="font-medium underline">
            처음부터 보기
          </Link>
        </Alert>
      ) : !data ? (
        <Alert variant="destructive">알림을 불러오지 못했습니다.</Alert>
      ) : (
        <NotificationList
          items={(data.items ?? []).map((item) => ({
            notificationId: item.notificationId ?? "",
            message: item.message ?? "",
            createdAt: item.createdAt,
            read: item.read ?? false,
            href: notificationHref(item),
          }))}
          nextHref={
            data.nextCursor ? `/notifications?cursor=${encodeURIComponent(data.nextCursor)}` : null
          }
          openAction={openNotification}
          readAllAction={readAllNotifications}
        />
      )}
    </>
  );
}
