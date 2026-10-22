"use client";

import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "./button";
import { Card } from "./card";
import { cn } from "./lib/cn";
import { formatDateTime } from "./lib/format";

type NotificationBellProps = {
  /** 알림함 화면 */
  href: string;
  /** 머리글을 그릴 때 서버가 읽은 안 읽은 수 */
  count: number;
  /** 안 읽은 수를 주는 앱의 Route Handler(BFF). `{count}`를 돌려준다 */
  countUrl: string;
  /** 다시 읽는 주기. 기본 60초 */
  intervalMs?: number;
};

/**
 * 머리글의 알림 링크와 안 읽은 수(docs/architecture/notification.md "인앱 알림"). 서버(레이아웃)가 그린 수를 따르고, 클라이언트
 * 이동으로 경로가 바뀌거나(레이아웃은 다시 그려지지 않는다) 머무는 동안 주기적으로 BFF에서 다시 읽는다. 실시간 푸시는 하지 않는다.
 * 다시 읽지 못하면 이전 수를 둔다.
 */
export function NotificationBell({
  href,
  count,
  countUrl,
  intervalMs = 60_000,
}: NotificationBellProps) {
  const [unread, setUnread] = useState(count);
  // 서버가 새 수를 그리면(Server Action 뒤 레이아웃 재검증) 그 수를 따른다.
  const [served, setServed] = useState(count);
  if (count !== served) {
    setServed(count);
    setUnread(count);
  }
  const refresh = useCallback(() => {
    fetch(countUrl, { cache: "no-store" })
      .then(async (response) =>
        response.ok ? ((await response.json()) as { count?: number }) : null,
      )
      .then((body) => {
        if (typeof body?.count === "number") {
          setUnread(body.count);
        }
      })
      .catch(() => {});
  }, [countUrl]);
  const pathname = usePathname();
  const firstPath = useRef(pathname);
  useEffect(() => {
    // 처음 그릴 때는 서버가 준 수가 최신이다.
    if (pathname !== firstPath.current) {
      firstPath.current = pathname;
      refresh();
    }
  }, [pathname, refresh]);
  useEffect(() => {
    const timer = setInterval(refresh, intervalMs);
    return () => clearInterval(timer);
  }, [refresh, intervalMs]);
  return (
    <a
      href={href}
      aria-label={unread > 0 ? `알림 ${unread}개 안 읽음` : "알림"}
      className="relative inline-flex items-center text-sm text-muted-foreground hover:text-foreground"
    >
      알림
      {unread > 0 ? (
        <span className="ml-1 rounded-full bg-destructive px-1.5 text-xs font-semibold text-destructive-foreground">
          {unread > 9 ? "9+" : unread}
        </span>
      ) : null}
    </a>
  );
}

export type NotificationItem = {
  notificationId: string;
  message: string;
  createdAt: string | undefined;
  read: boolean;
  /** 이 앱에서 열 화면. 없으면 읽음 표시만 한다 */
  href: string | null;
};

type NotificationListProps = {
  items: NotificationItem[];
  /** 다음 쪽(커서) 링크. 마지막 쪽이면 null */
  nextHref: string | null;
  /** 알림 하나를 읽음으로 하고 `href`로 간다. 폼 값은 `notificationId`, `href` */
  openAction: (form: FormData) => Promise<void>;
  /** 모두 읽음 */
  readAllAction: (form: FormData) => Promise<void>;
};

/** 알림함 목록. 최근 순이고, 알림을 열면 읽음이 된다. */
export function NotificationList({
  items,
  nextHref,
  openAction,
  readAllAction,
}: NotificationListProps) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">알림이 없습니다.</p>;
  }
  return (
    <div className="grid gap-4">
      <form action={readAllAction} className="flex justify-end">
        <Button type="submit" variant="outline" size="sm">
          모두 읽음
        </Button>
      </form>
      <Card>
        <ul className="divide-y">
          {items.map((item) => (
            <li
              key={item.notificationId}
              aria-label={item.message}
              className={cn(
                "flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm",
                item.read ? "text-muted-foreground" : "bg-secondary/40",
              )}
            >
              <div className="grid gap-0.5">
                <p className={item.read ? undefined : "font-medium text-foreground"}>
                  {item.read ? null : (
                    <span className="mr-2 rounded-full bg-primary px-1.5 py-0.5 text-xs text-primary-foreground">
                      새 알림
                    </span>
                  )}
                  {item.message}
                </p>
                <p className="text-xs text-muted-foreground">{formatDateTime(item.createdAt)}</p>
              </div>
              <form action={openAction}>
                <input type="hidden" name="notificationId" value={item.notificationId} />
                <input type="hidden" name="href" value={item.href ?? ""} />
                <Button
                  type="submit"
                  size="sm"
                  variant={item.href ? "default" : "outline"}
                  aria-label={`${item.message} ${item.href ? "보기" : "읽음"}`}
                  disabled={!item.href && item.read}
                >
                  {item.href ? "보기" : "읽음"}
                </Button>
              </form>
            </li>
          ))}
        </ul>
      </Card>
      {nextHref ? (
        <div>
          <Button asChild variant="outline" size="sm">
            <a href={nextHref}>더 보기</a>
          </Button>
        </div>
      ) : null}
    </div>
  );
}
