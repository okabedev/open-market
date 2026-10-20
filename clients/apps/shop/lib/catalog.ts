import { bff } from "./bff";

export type Category = {
  categoryId: string;
  name: string;
  children: { categoryId: string; name: string }[];
};

/**
 * 카테고리 트리(공개 API, catalog). 대분류를 고르면 그 아래 소분류 상품도 검색된다. catalog에 닿지 못하면 빈 목록이다(카테고리 없이도
 * 화면은 그린다).
 */
export async function loadCategories(): Promise<Category[]> {
  const catalog = await bff.publicClient("catalog");
  const result = await catalog.GET("/v1/categories").catch(() => null);
  const data = result?.response.ok ? result.data : undefined;
  if (!data) {
    return [];
  }
  return data.map((top) => ({
    categoryId: top.categoryId ?? "",
    name: top.name ?? "",
    children: (top.children ?? []).map((leaf) => ({
      categoryId: leaf.categoryId ?? "",
      name: leaf.name ?? "",
    })),
  }));
}
