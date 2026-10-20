import { Alert, PageHeader } from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { loadCategories } from "@/lib/catalog";
import { requireSeller } from "@/lib/session";
import { registerProduct } from "../actions";
import { newRow, ProductForm } from "../product-form";

/** 상품 등록(CAT-1). 등록하면 상품은 등록 중(DRAFT)이고, 상세 화면에서 판매를 시작한다. */
export default async function NewProductPage() {
  await requireSeller("/products/new");
  const categories = await loadCategories();
  return (
    <>
      <PageHeader
        title="상품 등록"
        description="등록한 뒤 상세 화면에서 판매를 시작하면 구매자 검색에 나옵니다."
      />
      {categories ? (
        <ProductForm
          mode="create"
          action={registerProduct}
          categories={categories}
          initial={{ imageFileIds: [], optionNames: [], skus: [newRow([])] }}
          hidden={{ idempotencyKey: randomUUID() }}
          submitText="등록"
        />
      ) : (
        <Alert variant="destructive">
          카테고리를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.
        </Alert>
      )}
    </>
  );
}
