import { bff } from "@/lib/bff";

/**
 * 판매자 ID → 상호. promotion·settlement는 상호를 갖지 않아 관리자 쿠폰·정산 목록이 쪽마다 member에 한 번 묻는다
 * (`/v1/admin/sellers/store-names`, 100개까지). member에 닿지 못하면 비어 있고 화면은 판매자 ID를 보인다.
 */
export async function storeNames(
  sellerIds: readonly (string | undefined)[],
): Promise<Map<string, string>> {
  const ids = [...new Set(sellerIds.filter((id): id is string => Boolean(id)))];
  if (ids.length === 0) {
    return new Map();
  }
  const member = await bff.serviceClient("member");
  const { data } = await member
    .GET("/v1/admin/sellers/store-names", { params: { query: { ids } } })
    .catch(() => ({ data: undefined }));
  return new Map(
    (data ?? []).flatMap(({ sellerId, storeName }) =>
      sellerId && storeName ? [[sellerId, storeName] as const] : [],
    ),
  );
}
