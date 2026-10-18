import { AppShell } from "@openmarket/ui";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { currentSession } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "오픈 마켓 판매자 센터",
};

// 세션(쿠키)을 읽으므로 요청마다 그린다.
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  return (
    <html lang="ko">
      <body>
        <AppShell appName="판매자 센터" roles={session?.roles ?? null}>
          {children}
        </AppShell>
      </body>
    </html>
  );
}
