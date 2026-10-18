import { AppShell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓",
};

const NAV: NavItem[] = [
  { href: "/my", label: "내 정보" },
  { href: "/my/addresses", label: "배송지" },
  { href: "/my/seller", label: "입점 신청" },
];

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  return (
    <html lang="ko">
      <body>
        <AppShell appName="오픈 마켓" nav={session ? NAV : []} roles={session?.roles ?? null}>
          {children}
        </AppShell>
      </body>
    </html>
  );
}
