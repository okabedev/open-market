import type { ReactNode } from "react";
import { AccountBar } from "./account-bar";
import { cn } from "./lib/cn";

export type NavItem = { href: string; label: string };

type AppShellProps = {
  /** 머리글의 앱 이름. 예: "오픈 마켓", "판매자 센터" */
  appName: string;
  nav?: NavItem[];
  roles: string[] | null;
  children: ReactNode;
  /** 머리글과 본문 폭. 관리자 표는 넓게 쓴다 */
  width?: "default" | "wide";
  /** 계정 앞에 붙는 것(알림 링크) */
  headerExtra?: ReactNode;
};

/** 앱 공통 틀: 머리글(앱 이름, 메뉴, 계정)과 본문. 서버 컴포넌트다. */
export function AppShell({
  appName,
  nav = [],
  roles,
  children,
  width = "default",
  headerExtra,
}: AppShellProps) {
  const container = cn("mx-auto w-full px-4 sm:px-6", width === "wide" ? "max-w-6xl" : "max-w-3xl");
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="border-b bg-card">
        <div className={cn(container, "flex h-14 items-center gap-6")}>
          <a href="/" className="font-semibold tracking-tight">
            {appName}
          </a>
          <nav aria-label="주 메뉴" className="flex flex-1 items-center gap-4 text-sm">
            {nav.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="text-muted-foreground hover:text-foreground"
              >
                {item.label}
              </a>
            ))}
          </nav>
          {headerExtra}
          <AccountBar roles={roles} />
        </div>
      </header>
      <main className={cn(container, "flex-1 py-8")}>{children}</main>
    </div>
  );
}

/** 화면 제목과 설명. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions}
    </div>
  );
}
