import { Alert, Button, cn, Input, Label } from "@openmarket/ui";
import Link from "next/link";
import { ProductCard } from "@/components/product-card";
import { bff } from "@/lib/bff";
import { loadCategories } from "@/lib/catalog";
import { searchHref, searchQuery, SORTS, type Sort } from "@/lib/search";

const PAGE_SIZE = 24;

/**
 * 상품 검색(SRC-1, SRC-3). 판매 중인 상품만 나오고 품절 상품은 뒤로 간다. 조건은 모두 URL 검색 조건이라 JS 없이 동작한다. 다음 쪽은 커서로
 * 넘긴다.
 */
export default async function ProductsPage({ searchParams }: PageProps<"/products">) {
  const query = searchQuery(await searchParams);
  const search = await bff.publicClient("search");
  const [result, categories] = await Promise.all([
    // 검색에 닿지 못하면 결과 칸에 문구를 보여 준다.
    search
      .GET("/v1/products", {
        params: {
          query: {
            ...(query.q ? { q: query.q } : {}),
            ...(query.categoryId ? { categoryId: query.categoryId } : {}),
            ...(query.minPrice !== undefined ? { minPrice: query.minPrice } : {}),
            ...(query.maxPrice !== undefined ? { maxPrice: query.maxPrice } : {}),
            sort: query.sort,
            ...(query.cursor ? { cursor: query.cursor } : {}),
            size: PAGE_SIZE,
          },
        },
      })
      .catch(() => null),
    loadCategories(),
  ]);
  const data = result?.response.ok ? result.data : undefined;
  const status = result?.response.status;
  const top = categories.find(
    (category) =>
      category.categoryId === query.categoryId ||
      category.children.some((child) => child.categoryId === query.categoryId),
  );
  const items = data?.items ?? [];
  return (
    <div className="grid gap-6">
      <form action="/products" className="flex gap-2" role="search">
        <Label htmlFor="q" className="sr-only">
          검색어
        </Label>
        <Input
          id="q"
          name="q"
          type="search"
          maxLength={100}
          defaultValue={query.q}
          placeholder="상품 이름, 상호로 찾기"
        />
        {query.categoryId ? (
          <input type="hidden" name="categoryId" value={query.categoryId} />
        ) : null}
        {query.sort !== "RELEVANCE" ? <input type="hidden" name="sort" value={query.sort} /> : null}
        <Button type="submit">검색</Button>
      </form>

      <div className="grid gap-6 md:grid-cols-[12rem_1fr]">
        <aside className="grid content-start gap-6 text-sm">
          <nav aria-label="카테고리" className="grid gap-1">
            <h2 className="mb-1 font-semibold">카테고리</h2>
            <FilterLink
              href={searchHref(query, { categoryId: undefined })}
              active={!query.categoryId}
            >
              전체
            </FilterLink>
            {categories.map((category) => (
              <div key={category.categoryId} className="grid gap-1">
                <FilterLink
                  href={searchHref(query, { categoryId: category.categoryId })}
                  active={query.categoryId === category.categoryId}
                >
                  {category.name}
                </FilterLink>
                {top?.categoryId === category.categoryId
                  ? category.children.map((child) => (
                      <FilterLink
                        key={child.categoryId}
                        href={searchHref(query, { categoryId: child.categoryId })}
                        active={query.categoryId === child.categoryId}
                        className="pl-4"
                      >
                        {child.name}
                      </FilterLink>
                    ))
                  : null}
              </div>
            ))}
          </nav>
          <form action="/products" className="grid gap-2" aria-label="가격대">
            <h2 className="font-semibold">가격대</h2>
            {query.q ? <input type="hidden" name="q" value={query.q} /> : null}
            {query.categoryId ? (
              <input type="hidden" name="categoryId" value={query.categoryId} />
            ) : null}
            {query.sort !== "RELEVANCE" ? (
              <input type="hidden" name="sort" value={query.sort} />
            ) : null}
            <div className="flex items-center gap-1">
              <Input
                name="minPrice"
                type="number"
                min={0}
                step={10}
                aria-label="최저 가격"
                placeholder="최저"
                defaultValue={query.minPrice}
              />
              <span aria-hidden>~</span>
              <Input
                name="maxPrice"
                type="number"
                min={0}
                step={10}
                aria-label="최고 가격"
                placeholder="최고"
                defaultValue={query.maxPrice}
              />
            </div>
            <Button type="submit" variant="outline" size="sm">
              적용
            </Button>
          </form>
        </aside>

        <section className="grid content-start gap-4" aria-label="검색 결과">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground">
              {query.q ? `"${query.q}" ` : ""}
              {data ? `${(data.totalCount ?? 0).toLocaleString("ko-KR")}개 상품` : ""}
            </p>
            <nav aria-label="정렬" className="flex flex-wrap gap-3 text-sm">
              {(Object.keys(SORTS) as Sort[]).map((sort) => (
                <FilterLink
                  key={sort}
                  href={searchHref(query, { sort })}
                  active={query.sort === sort}
                >
                  {SORTS[sort]}
                </FilterLink>
              ))}
            </nav>
          </div>
          {!data ? (
            <Alert variant="destructive">
              {status === 400 ? "검색 조건이 올바르지 않습니다." : "상품을 불러오지 못했습니다."}
            </Alert>
          ) : items.length === 0 ? (
            <Alert>조건에 맞는 상품이 없습니다.</Alert>
          ) : (
            <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {items.map((item) => (
                <li key={item.productId}>
                  <ProductCard {...item} />
                </li>
              ))}
            </ul>
          )}
          <div className="flex justify-between">
            {query.cursor ? (
              <Button asChild variant="outline" size="sm">
                <Link href={searchHref(query, {})}>처음으로</Link>
              </Button>
            ) : (
              <span />
            )}
            {data?.nextCursor ? (
              <Button asChild variant="outline" size="sm">
                <Link href={searchHref(query, { cursor: data.nextCursor })}>다음</Link>
              </Button>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}

function FilterLink({
  href,
  active,
  className,
  children,
}: {
  href: string;
  active: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        active ? "font-semibold text-foreground" : "text-muted-foreground hover:text-foreground",
        className,
      )}
    >
      {children}
    </Link>
  );
}
