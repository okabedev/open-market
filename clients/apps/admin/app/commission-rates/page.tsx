import {
  Alert,
  Badge,
  Card,
  ConfirmActionForm,
  formatDateTime,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { bpToPercent } from "@/lib/commission";
import { noticeMessage } from "@/lib/review";
import { requireSession } from "@/lib/session";
import { resetCommissionRate } from "./actions";
import { RateForm } from "./rate-form";

type Rate = { categoryId?: string; rateBp?: number; updatedAt?: string };

/**
 * 수수료율(STL-1). 소분류 카테고리마다 요율을 정하거나 지워 기본 요율로 돌린다. settlement는 요율을 정한 카테고리만 주므로 이름은 catalog
 * 카테고리 트리로 붙인다. 트리에 없는 카테고리에 정한 요율은 쓰이지 않아 따로 보인다. 바꾼 요율은 그 뒤에 생기는 정산 대상부터 쓴다.
 */
export default async function CommissionRatesPage({
  searchParams,
}: PageProps<"/commission-rates">) {
  await requireSession("/commission-rates");
  const notice = noticeMessage((await searchParams).notice);
  const [settlement, catalog] = await Promise.all([
    bff.serviceClient("settlement"),
    bff.serviceClient("catalog"),
  ]);
  const [rates, categories] = await Promise.all([
    settlement.GET("/v1/admin/commission-rates").catch(() => ({ data: undefined })),
    catalog.GET("/v1/categories").catch(() => ({ data: undefined })),
  ]);
  if (!rates.data || !categories.data) {
    return <Alert variant="destructive">수수료율이나 카테고리를 불러오지 못했습니다.</Alert>;
  }
  const defaultRateBp = rates.data.defaultRateBp ?? 0;
  const byCategory = new Map<string, Rate>(
    (rates.data.rates ?? []).map((rate) => [rate.categoryId ?? "", rate]),
  );
  const leafIds = new Set(
    categories.data.flatMap((top) => (top.children ?? []).map((leaf) => leaf.categoryId ?? "")),
  );
  const orphans = (rates.data.rates ?? []).filter((rate) => !leafIds.has(rate.categoryId ?? ""));
  const placeholder = String(defaultRateBp / 100);
  return (
    <>
      <PageHeader
        title="수수료율"
        description={`정하지 않은 카테고리는 기본 요율 ${bpToPercent(defaultRateBp)}입니다. 바꾼 요율은 이 뒤에 구매확정되는 주문부터 쓰고, 이미 생긴 정산 대상은 바뀌지 않습니다.`}
      />
      {notice ? (
        <Alert variant="success" className="mb-4">
          {notice}
        </Alert>
      ) : null}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>카테고리</TableHead>
              <TableHead>지금 요율</TableHead>
              <TableHead>바꾸기</TableHead>
              <TableHead>
                <span className="sr-only">기본으로</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {categories.data.flatMap((top) =>
              (top.children ?? []).map((leaf) => {
                const id = leaf.categoryId ?? "";
                const rate = byCategory.get(id);
                const name = `${top.name} > ${leaf.name}`;
                return (
                  <TableRow key={id}>
                    <TableCell className="font-medium">{name}</TableCell>
                    <TableCell className="whitespace-nowrap">
                      {rate?.rateBp !== undefined ? (
                        <span className="grid gap-0.5">
                          <span>{bpToPercent(rate.rateBp)}</span>
                          <span className="text-xs text-muted-foreground">
                            {formatDateTime(rate.updatedAt)}
                          </span>
                        </span>
                      ) : (
                        <Badge variant="secondary">기본 {bpToPercent(defaultRateBp)}</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <RateForm
                        categoryId={id}
                        categoryName={name}
                        current={rate?.rateBp !== undefined ? String(rate.rateBp / 100) : undefined}
                        placeholder={placeholder}
                      />
                    </TableCell>
                    <TableCell>
                      {rate ? (
                        <ConfirmActionForm
                          action={resetCommissionRate}
                          hidden={{ categoryId: id }}
                          title={`${name} 요율을 기본으로 돌릴까요?`}
                          description={`기본 요율 ${bpToPercent(defaultRateBp)}을 씁니다. 이 뒤에 구매확정되는 주문부터 적용합니다.`}
                          confirmText="기본으로"
                          variant="outline"
                        >
                          기본으로
                        </ConfirmActionForm>
                      ) : null}
                    </TableCell>
                  </TableRow>
                );
              }),
            )}
          </TableBody>
        </Table>
      </Card>
      {orphans.length > 0 ? (
        <section aria-labelledby="orphans-heading" className="mt-6 grid gap-2">
          <h2 id="orphans-heading" className="text-lg font-semibold">
            카테고리 트리에 없는 요율
          </h2>
          <p className="text-sm text-muted-foreground">
            지금 카테고리에 없어 쓰이지 않습니다. 지워도 됩니다.
          </p>
          <ul className="grid gap-2 text-sm">
            {orphans.map((rate) => (
              <li key={rate.categoryId} className="flex items-center gap-3">
                <span className="font-mono text-xs">{rate.categoryId}</span>
                <span>{bpToPercent(rate.rateBp ?? 0)}</span>
                <ConfirmActionForm
                  action={resetCommissionRate}
                  hidden={{ categoryId: rate.categoryId ?? "" }}
                  title="이 요율을 지울까요?"
                  description="카테고리 트리에 없어 쓰이지 않는 요율입니다."
                  confirmText="지우기"
                  variant="outline"
                >
                  지우기
                </ConfirmActionForm>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </>
  );
}
