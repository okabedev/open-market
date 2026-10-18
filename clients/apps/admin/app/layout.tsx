import { AppShell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓 관리자",
};

const NAV: NavItem[] = [
  { href: "/seller-applications", label: "입점 심사" },
  { href: "/sellers", label: "판매자" },
];

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  return (
    <html lang="ko">
      <body>
        <AppShell
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
