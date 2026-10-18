import { Button } from "@openmarket/ui";

type PagerProps = {
  basePath: string;
  status: string | undefined;
  page: number;
  size: number;
  totalCount: number;
};

/** 이전·다음 쪽 링크. 관리자 목록은 쪽 번호를 쓴다(conventions.md "REST API"). */
export function Pager({ basePath, status, page, size, totalCount }: PagerProps) {
  const lastPage = Math.max(0, Math.ceil(totalCount / size) - 1);
  const href = (target: number) => {
    const query = new URLSearchParams();
    if (status) {
      query.set("status", status);
    }
    query.set("page", String(target));
    return `${basePath}?${query.toString()}`;
  };
  return (
    <nav
      aria-label="쪽"
      className="mt-4 flex items-center justify-between text-sm text-muted-foreground"
    >
      <span>
        전체 {totalCount}건 · {page + 1}/{lastPage + 1}쪽
      </span>
      <div className="flex gap-2">
        {page > 0 ? (
          <Button asChild variant="outline" size="sm">
            <a href={href(page - 1)}>이전</a>
          </Button>
        ) : null}
        {page < lastPage ? (
          <Button asChild variant="outline" size="sm">
            <a href={href(page + 1)}>다음</a>
          </Button>
        ) : null}
      </div>
    </nav>
  );
}
