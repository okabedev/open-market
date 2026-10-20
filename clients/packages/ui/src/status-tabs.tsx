import { cn } from "./lib/cn";

type StatusTabsProps = {
  basePath: string;
  tabs: { status: string | undefined; label: string }[];
  current: string | undefined;
};

/** 상태 필터. 링크라서 JS 없이 동작한다. */
export function StatusTabs({ basePath, tabs, current }: StatusTabsProps) {
  return (
    <nav aria-label="상태" className="mb-4 flex gap-1 border-b">
      {tabs.map((tab) => {
        const active = tab.status === current;
        return (
          <a
            key={tab.label}
            href={tab.status ? `${basePath}?status=${tab.status}` : basePath}
            aria-current={active ? "page" : undefined}
            className={cn(
              "-mb-px border-b-2 px-3 py-2 text-sm",
              active
                ? "border-primary font-medium text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {tab.label}
          </a>
        );
      })}
    </nav>
  );
}
