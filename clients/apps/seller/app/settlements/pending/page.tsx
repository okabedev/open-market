import { Alert, Button, PageHeader } from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import { requireSeller } from "@/lib/session";
import { SettlementItems } from "../settlement-parts";

const PAGE_SIZE = 20;

/** 정산 예정 내역(STL-2). 구매확정했지만 아직 정산이 확정되지 않은 하위 주문이다. 정산 기간이 끝나면 다음 정산에 들어간다. */
export default async function PendingSettlementPage({
  searchParams,
}: PageProps<"/settlements/pending">) {
  await requireSeller("/settlements/pending");
  const { cursor } = await searchParams;
  const settlement = await bff.serviceClient("settlement");
  const { data } = await settlement
    .GET("/v1/seller/settlement-items/pending", {
      params: { query: { size: PAGE_SIZE, ...(typeof cursor === "string" ? { cursor } : {}) } },
    })
    .catch(() => ({ data: undefined }));
  return (
    <>
      <PageHeader
        title="정산 예정 내역"
        description="구매확정했지만 아직 정산이 확정되지 않은 주문입니다. 정산 기간이 끝나면 확정됩니다."
        actions={
          <Button asChild variant="outline">
            <Link href="/settlements">정산</Link>
          </Button>
        }
      />
      {!data ? (
        <Alert variant="destructive">정산 예정 내역을 불러오지 못했습니다.</Alert>
      ) : (
        <SettlementItems
          items={data.items ?? []}
          basePath="/settlements/pending"
          cursor={typeof cursor === "string" ? cursor : undefined}
          nextCursor={data.nextCursor}
        />
      )}
    </>
  );
}
