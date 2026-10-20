import type { CategoryOption } from "@/app/products/product-form";
import { bff } from "./bff";

/** 카테고리 트리(공개 API). 상품은 소분류에만 붙으므로 폼은 소분류만 고르게 한다. */
export async function loadCategories(): Promise<CategoryOption[] | null> {
  const catalog = await bff.serviceClient("catalog");
  const { data, response } = await catalog.GET("/v1/categories");
  if (!response.ok || !data) {
    return null;
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
