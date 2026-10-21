import { AppShell, type NavItem } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { isSeller } from "@/lib/access";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓 판매자 센터",
};

const NAV: NavItem[] = [
  { href: "/products", label: "상품" },
  { href: "/orders", label: "주문" },
];

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  return (
    <html lang="ko">
      <body>
        <AppShell
          appName="판매자 센터"
          nav={session && isSeller(session.roles) ? NAV : []}
          roles={session?.roles ?? null}
          width="wide"
        >
          {children}
        </AppShell>
      </body>
    </html>
  );
}
