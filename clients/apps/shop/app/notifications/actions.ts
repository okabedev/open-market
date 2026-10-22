"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { safeAppPath } from "@/lib/notifications";

/** 알림을 읽음으로 하고 그 화면으로 간다. 이미 읽었거나 지워진 알림이어도 화면으로는 간다. */
export async function openNotification(form: FormData): Promise<void> {
  const notificationId = z.uuid().safeParse(form.get("notificationId"));
  if (notificationId.success) {
    const notification = await bff.serviceClient("notification");
    await notification.POST("/v1/notifications/{notificationId}/read", {
      params: { path: { notificationId: notificationId.data } },
    });
  }
  // 머리글의 안 읽은 수는 루트 레이아웃이 그린다. 클라이언트 이동은 레이아웃을 다시 그리지 않으므로 재검증한다.
  revalidatePath("/", "layout");
  const href = form.get("href");
  redirect(typeof href === "string" && href ? safeAppPath(href) : "/notifications");
}

/** 지금까지 온 알림을 모두 읽음으로 한다. */
export async function readAllNotifications(): Promise<void> {
  const notification = await bff.serviceClient("notification");
  await notification.POST("/v1/notifications/read-all");
  revalidatePath("/", "layout");
  redirect("/notifications");
}
