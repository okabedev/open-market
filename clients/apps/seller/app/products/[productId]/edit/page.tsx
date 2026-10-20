import { Alert, PageHeader } from "@openmarket/ui";
import { notFound, redirect } from "next/navigation";
import { bff } from "@/lib/bff";
import { loadCategories } from "@/lib/catalog";
import { requireSeller } from "@/lib/session";
import { updateProduct } from "../../actions";
import { newRow } from "@/lib/product-rules";
import { ProductForm } from "../../product-form";

/** 상품 수정(CAT-2). 제재된 상품은 고칠 수 없어 상세로 돌려보낸다. */
export default async function EditProductPage({ params }: PageProps<"/products/[productId]/edit">) {
  const { productId } = await params;
  await requireSeller(`/products/${productId}/edit`);
  const catalog = await bff.serviceClient("catalog");
  const [{ data: product, response }, categories] = await Promise.all([
    catalog.GET("/v1/seller/products/{productId}", { params: { path: { productId } } }),
    loadCategories(),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!product || !categories) {
    return <Alert variant="destructive">상품을 불러오지 못했습니다.</Alert>;
  }
  if (product.status === "BANNED") {
    redirect(`/products/${productId}`);
  }
  const path = product.categoryPath ?? [];
  return (
    <>
      <PageHeader title="상품 수정" description={product.name} />
      <ProductForm
        mode="edit"
        action={updateProduct}
        categories={categories}
        initial={{
          categoryId: path[path.length - 1]?.categoryId,
          name: product.name,
          description: product.description,
          imageFileIds: product.imageFileIds ?? [],
          optionNames: product.optionNames ?? [],
          skus: (product.skus ?? []).map((sku) =>
            newRow(sku.optionValues ?? [], sku.skuId, String(sku.price ?? "")),
          ),
        }}
        hidden={{ productId }}
        submitText="저장"
      />
    </>
  );
}
