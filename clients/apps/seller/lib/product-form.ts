import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { PRODUCT_LIMITS } from "./product-rules";

export { combineOptions, parseOptionValues, PRODUCT_LIMITS } from "./product-rules";

/**
 * 상품 등록·수정 폼의 입력 규칙(Server Action에서 쓴다). 원본은 catalog의 규칙(docs/architecture/catalog.md "규칙")이고, 여기서는
 * 같은 규칙으로 먼저 걸러 필드마다 문구를 보여 준다. 서비스가 다시 검사한다.
 */
const text = (label: string, max: number) =>
  z
    .string()
    .trim()
    .min(1, `${label}을(를) 입력해 주세요.`)
    .max(max, `${label}은(는) ${max.toLocaleString("ko-KR")}자까지입니다.`);

const priceField = z
  .number({ error: "가격을 숫자로 입력해 주세요." })
  .int("가격은 원 단위 정수입니다.")
  .min(PRODUCT_LIMITS.minPrice, "가격은 100원부터 1억 원까지입니다.")
  .max(PRODUCT_LIMITS.maxPrice, "가격은 100원부터 1억 원까지입니다.")
  .refine((price) => price % 10 === 0, "가격은 10원 단위입니다.");

const stockField = z
  .number({ error: "재고를 숫자로 입력해 주세요." })
  .int("재고는 정수입니다.")
  .min(0, "재고는 0부터 999,999까지입니다.")
  .max(PRODUCT_LIMITS.maxStock, "재고는 0부터 999,999까지입니다.");

const skuSchema = z.object({
  skuId: z.uuid().optional(),
  optionValues: z.array(
    z.string().trim().min(1, "옵션 값을 입력해 주세요.").max(30, "옵션 값은 30자까지입니다."),
  ),
  price: priceField,
  initialStock: stockField,
});

export const productFormSchema = z
  .object({
    categoryId: z.uuid("카테고리를 골라 주세요."),
    name: z
      .string()
      .trim()
      .min(1, "상품 이름을 입력해 주세요.")
      .max(100, "상품 이름은 100자까지입니다."),
    description: z
      .string()
      .trim()
      .min(1, "상품 설명을 입력해 주세요.")
      .max(5_000, "상품 설명은 5,000자까지입니다."),
    imageFileIds: z
      .array(z.uuid())
      .max(PRODUCT_LIMITS.images, "이미지는 10장까지입니다.")
      .refine((ids) => new Set(ids).size === ids.length, "같은 이미지를 두 번 넣었습니다."),
    optionNames: z
      .array(text("옵션 이름", 20))
      .max(PRODUCT_LIMITS.options, "옵션은 2개까지입니다.")
      .refine((names) => new Set(names).size === names.length, "옵션 이름이 겹칩니다."),
    skus: z
      .array(skuSchema, { error: "옵션 조합을 하나 이상 넣어 주세요." })
      .min(1, "옵션 조합을 하나 이상 넣어 주세요.")
      .max(PRODUCT_LIMITS.skus, "옵션 조합은 100개까지입니다."),
  })
  .superRefine((product, context) => {
    const seen = new Set<string>();
    product.skus.forEach((sku, index) => {
      if (sku.optionValues.length !== product.optionNames.length) {
        context.addIssue({
          code: "custom",
          path: ["skus", index, "optionValues"],
          message: "옵션마다 값을 하나씩 넣어 주세요.",
        });
        return;
      }
      const key = JSON.stringify(sku.optionValues);
      if (seen.has(key)) {
        context.addIssue({
          code: "custom",
          path: ["skus", index, "optionValues"],
          message: "옵션 조합이 겹칩니다.",
        });
      }
      seen.add(key);
    });
  });

export type ProductFormData = z.infer<typeof productFormSchema>;

/** 폼에서 읽는 필드. 이미지·옵션·SKU는 클라이언트 폼이 JSON으로 숨은 입력에 넣는다. */
const FIELDS = [
  "categoryId",
  "name",
  "description",
  "imageFileIds",
  "optionNames",
  "skus",
] as const;

function json(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return undefined;
  }
}

/**
 * 상품 폼을 읽고 검사한다. 실패하면 필드 오류와 입력값(다시 채울 값)을 담은 폼 결과를 준다. JSON 필드가 깨졌으면 그 필드가 없는 것으로
 * 본다.
 */
export function readProductForm(
  form: FormData,
): { success: true; data: ProductFormData } | { success: false; state: FormState } {
  const values = Object.fromEntries(FIELDS.map((name) => [name, String(form.get(name) ?? "")]));
  const parsed = productFormSchema.safeParse({
    categoryId: values.categoryId,
    name: values.name,
    description: values.description,
    imageFileIds: json(values.imageFileIds ?? "") ?? [],
    optionNames: json(values.optionNames ?? "") ?? [],
    skus: json(values.skus ?? ""),
  });
  if (!parsed.success) {
    return {
      success: false,
      state: {
        status: "error",
        message: "입력을 확인해 주세요.",
        fieldErrors: fieldErrorsFrom(parsed.error.issues),
        values,
      },
    };
  }
  return { success: true, data: parsed.data };
}

/** 재고 조정(CAT-3). 증감 수량은 0이 아닌 정수이고 사유는 선택이다. */
export const stockAdjustmentSchema = z.object({
  delta: z.coerce
    .number({ error: "증감 수량을 숫자로 입력해 주세요." })
    .int("증감 수량은 정수입니다.")
    .min(-PRODUCT_LIMITS.maxStock, "증감 수량은 -999,999부터 999,999까지입니다.")
    .max(PRODUCT_LIMITS.maxStock, "증감 수량은 -999,999부터 999,999까지입니다.")
    .refine((delta) => delta !== 0, "0이 아닌 수량을 입력해 주세요."),
  reason: z.string().trim().max(100, "사유는 100자까지입니다."),
});

/** 상품 상태 이름(docs/glossary.md "상품"). */
export const STATUS_LABELS = {
  DRAFT: "등록 중",
  ON_SALE: "판매 중",
  STOPPED: "판매 중지",
  BANNED: "판매 제한",
} as const;

export type ProductStatus = keyof typeof STATUS_LABELS;

export const STATUSES = Object.keys(STATUS_LABELS) as ProductStatus[];

export function statusLabel(status: string | undefined): string {
  return status && status in STATUS_LABELS ? STATUS_LABELS[status as ProductStatus] : "-";
}

/** 판매 중지 사유(docs/glossary.md "판매 중지 사유"). */
export const STOP_REASON_LABELS: Record<string, string> = {
  SELLER: "직접 중지했습니다.",
  SELLER_SUSPENDED: "판매자 정지로 멈췄습니다. 정지가 풀리면 다시 판매됩니다.",
};
