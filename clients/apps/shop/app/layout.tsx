import { AppShell, NotificationBell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { unreadCount } from "@/lib/notification-count";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓",
};

const PUBLIC_NAV: NavItem[] = [
  { href: "/products", label: "상품" },
  { href: "/coupons", label: "쿠폰" },
];

const NAV: NavItem[] = [
  ...PUBLIC_NAV,
  { href: "/cart", label: "장바구니" },
  { href: "/orders", label: "주문 내역" },
  { href: "/my/coupons", label: "내 쿠폰" },
  { href: "/my/reviews", label: "내 리뷰" },
  { href: "/my", label: "내 정보" },
  { href: "/my/addresses", label: "배송지" },
  { href: "/my/seller", label: "입점 신청" },
];

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  // 화면을 옮길 때마다 서버가 안 읽은 수를 그린다. 머문 동안은 NotificationBell이 주기적으로 다시 읽는다.
  const bell = session ? (
    <NotificationBell
      href="/notifications"
      count={await unreadCount()}
      countUrl="/api/notifications/unread-count"
    />
  ) : null;
  return (
    <html lang="ko">
      <body>
        <AppShell
          headerExtra={bell}
          appName="오픈 마켓"
          width="wide"
          nav={session ? NAV : PUBLIC_NAV}
          roles={session?.roles ?? null}
        >
          {children}
        </AppShell>
      </body>
    </html>
  );
}
