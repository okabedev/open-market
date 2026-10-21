import { AppShell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓",
};

const PUBLIC_NAV: NavItem[] = [{ href: "/products", label: "상품" }];

const NAV: NavItem[] = [
  ...PUBLIC_NAV,
  { href: "/cart", label: "장바구니" },
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
        <AppShell
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
