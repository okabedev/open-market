import { AppShell, NotificationBell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { unreadCount } from "@/lib/notification-count";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓 관리자",
};

const NAV: NavItem[] = [
  { href: "/seller-applications", label: "입점 심사" },
  { href: "/sellers", label: "판매자" },
  { href: "/products", label: "상품" },
  { href: "/coupons", label: "쿠폰" },
  { href: "/reviews", label: "리뷰" },
  { href: "/claims", label: "클레임 중재" },
  { href: "/payment-failures", label: "결제 실패" },
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
          appName="오픈 마켓 관리자"
          nav={session ? NAV : []}
          roles={session?.roles ?? null}
          width="wide"
        >
          {children}
        </AppShell>
      </body>
    </html>
  );
}
