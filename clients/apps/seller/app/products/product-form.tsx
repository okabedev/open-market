"use client";

import Link from "next/link";
import {
  Button,
  FormAlert,
  idleState,
  Input,
  Label,
  SubmitButton,
  TextAreaField,
  TextField,
  type FormState,
  ProductImage,
} from "@openmarket/ui";
import { useActionState, useState, type ReactNode } from "react";
import {
  combineOptions,
  newRow,
  parseOptionValues,
  PRODUCT_LIMITS,
  type SkuRow,
} from "@/lib/product-rules";

export type CategoryOption = {
  categoryId: string;
  name: string;
  children: { categoryId: string; name: string }[];
};

export type ProductFormInitial = {
  categoryId?: string;
  name?: string;
  description?: string;
  imageFileIds: string[];
  optionNames: string[];
  skus: SkuRow[];
};

type ProductFormProps = {
  /** create: 등록(옵션 이름을 정한다). edit: 수정(옵션 이름은 그대로, 기존 SKU는 가격만) */
  mode: "create" | "edit";
  action: (state: FormState, form: FormData) => Promise<FormState>;
  categories: CategoryOption[];
  initial: ProductFormInitial;
  /** 멱등성 키, 상품 ID 같은 숨은 값 */
  hidden: Record<string, string>;
  submitText: string;
};

function sameValues(a: readonly string[], b: readonly string[]): boolean {
  return a.length === b.length && a.every((value, index) => value === b[index]);
}

function toNumber(value: string): number | null {
  return value.trim() === "" ? null : Number(value);
}

/**
 * 상품 등록·수정 폼(CAT-1, CAT-2). 이미지는 고르는 즉시 BFF(`/api/files`)로 올리고 `fileId`만 갖는다. 옵션 값을 쉼표로 적고 조합을
 * 만들면 SKU 줄이 생긴다. 입력 즉시 확인은 HTML 속성으로만 하고, 규칙 검사는 Server Action과 catalog가 한다(ADR 0015).
 */
export function ProductForm({
  mode,
  action,
  categories,
  initial,
  hidden,
  submitText,
}: ProductFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  const [images, setImages] = useState(initial.imageFileIds);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [optionNames, setOptionNames] = useState(initial.optionNames);
  const [optionValues, setOptionValues] = useState<string[]>(() =>
    initial.optionNames.map(() => ""),
  );
  const [optionError, setOptionError] = useState<string | null>(null);
  const [skus, setSkus] = useState(initial.skus);
  const [bulkPrice, setBulkPrice] = useState("");
  const [bulkStock, setBulkStock] = useState("");
  const errors = state.fieldErrors ?? {};
  const editing = mode === "edit";

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) {
      return;
    }
    setUploadError(null);
    setUploading(true);
    let count = images.length;
    try {
      for (const file of Array.from(files)) {
        if (count >= PRODUCT_LIMITS.images) {
          setUploadError("이미지는 10장까지입니다.");
          break;
        }
        if (file.size > PRODUCT_LIMITS.imageBytes) {
          setUploadError(`${file.name}: 이미지는 10MB까지 올릴 수 있습니다.`);
          continue;
        }
        const body = new FormData();
        body.set("file", file);
        const response = await fetch("/api/files", { method: "POST", body });
        const result = (await response.json().catch(() => ({}))) as {
          fileId?: string;
          detail?: string;
        };
        if (!response.ok || !result.fileId) {
          setUploadError(`${file.name}: ${result.detail ?? "이미지를 올리지 못했습니다."}`);
          continue;
        }
        const fileId = result.fileId;
        count += 1;
        setImages((current) => [...current, fileId]);
      }
    } finally {
      setUploading(false);
    }
  }

  function moveImage(index: number, offset: number) {
    setImages((current) => {
      const next = [...current];
      const target = index + offset;
      if (target < 0 || target >= next.length) {
        return current;
      }
      [next[index], next[target]] = [next[target] as string, next[index] as string];
      return next;
    });
  }

  function generateRows() {
    const lists = optionNames.map((_, index) => parseOptionValues(optionValues[index] ?? ""));
    if (optionNames.some((name) => name.trim() === "")) {
      setOptionError("옵션 이름을 입력해 주세요.");
      return;
    }
    if (lists.some((values) => values.length === 0)) {
      setOptionError("옵션마다 값을 하나 이상 넣어 주세요.");
      return;
    }
    const combinations = combineOptions(lists);
    if (combinations.length > PRODUCT_LIMITS.skus) {
      setOptionError(
        `옵션 조합은 ${PRODUCT_LIMITS.skus}개까지입니다. 지금 ${combinations.length}개입니다.`,
      );
      return;
    }
    setOptionError(null);
    setSkus((current) =>
      combinations.map(
        (values) => current.find((row) => sameValues(row.optionValues, values)) ?? newRow(values),
      ),
    );
  }

  function setOptionCount(count: number) {
    setOptionNames((current) => Array.from({ length: count }, (_, index) => current[index] ?? ""));
    setOptionValues((current) => Array.from({ length: count }, (_, index) => current[index] ?? ""));
    if (count === 0) {
      setSkus((current) => [current.find((row) => row.optionValues.length === 0) ?? newRow([])]);
    } else {
      setSkus([]);
    }
  }

  function updateRow(key: string, change: Partial<SkuRow>) {
    setSkus((current) => current.map((row) => (row.key === key ? { ...row, ...change } : row)));
  }

  function applyBulk() {
    setSkus((current) =>
      current.map((row) => ({
        ...row,
        ...(bulkPrice ? { price: bulkPrice } : {}),
        ...(bulkStock && !row.skuId ? { initialStock: bulkStock } : {}),
      })),
    );
  }

  const skuPayload = skus.map((row) => ({
    ...(row.skuId ? { skuId: row.skuId } : {}),
    optionValues: row.optionValues,
    price: toNumber(row.price),
    initialStock: row.skuId ? 0 : toNumber(row.initialStock),
  }));

  return (
    <form action={formAction} className="grid gap-8">
      {Object.entries(hidden).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <input type="hidden" name="imageFileIds" value={JSON.stringify(images)} />
      <input
        type="hidden"
        name="optionNames"
        value={JSON.stringify(optionNames.map((name) => name.trim()))}
      />
      <input type="hidden" name="skus" value={JSON.stringify(skuPayload)} />
      <FormAlert state={state} />

      <Section title="기본 정보">
        <div className="grid gap-1.5">
          <Label htmlFor="categoryId">카테고리</Label>
          <select
            id="categoryId"
            name="categoryId"
            required
            defaultValue={state.values?.categoryId ?? initial.categoryId ?? ""}
            aria-invalid={errors.categoryId ? true : undefined}
            className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
          >
            <option value="" disabled>
              소분류를 골라 주세요
            </option>
            {categories.map((top) => (
              <optgroup key={top.categoryId} label={top.name}>
                {top.children.map((leaf) => (
                  <option key={leaf.categoryId} value={leaf.categoryId}>
                    {top.name} &gt; {leaf.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
          {errors.categoryId ? (
            <p className="text-xs text-destructive">{errors.categoryId}</p>
          ) : null}
        </div>
        <TextField
          name="name"
          label="상품 이름"
          required
          maxLength={100}
          defaultValue={state.values?.name ?? initial.name}
          error={errors.name}
        />
        <TextAreaField
          name="description"
          label="상품 설명"
          required
          maxLength={5_000}
          rows={6}
          hint="일반 텍스트로 적습니다. 5,000자까지입니다."
          defaultValue={state.values?.description ?? initial.description}
          error={errors.description}
        />
      </Section>

      <Section
        title="이미지"
        description="JPEG, PNG, WebP 10장까지, 한 장에 10MB까지입니다. 첫 이미지가 대표 이미지입니다. 판매를 시작하려면 한 장 이상 있어야 합니다."
      >
        {images.length > 0 ? (
          <ol className="flex flex-wrap gap-3">
            {images.map((fileId, index) => (
              <li key={fileId} className="grid gap-1 text-xs">
                <ProductImage fileId={fileId} size={96} alt={`이미지 ${index + 1}`} />
                <div className="flex gap-1">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => moveImage(index, -1)}
                    disabled={index === 0}
                    aria-label={`이미지 ${index + 1} 앞으로`}
                  >
                    ←
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => setImages((current) => current.filter((id) => id !== fileId))}
                    aria-label={`이미지 ${index + 1} 빼기`}
                  >
                    빼기
                  </Button>
                </div>
                {index === 0 ? <span className="text-muted-foreground">대표</span> : null}
              </li>
            ))}
          </ol>
        ) : null}
        <div className="grid gap-1.5">
          <Label htmlFor="images">이미지 올리기</Label>
          <Input
            id="images"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            disabled={uploading || images.length >= PRODUCT_LIMITS.images}
            onChange={(event) => {
              void upload(event.currentTarget.files);
              event.currentTarget.value = "";
            }}
          />
          {uploading ? <p className="text-xs text-muted-foreground">올리는 중…</p> : null}
          {uploadError ? <p className="text-xs text-destructive">{uploadError}</p> : null}
          {errors.imageFileIds ? (
            <p className="text-xs text-destructive">{errors.imageFileIds}</p>
          ) : null}
        </div>
      </Section>

      <Section
        title="옵션과 가격·재고"
        description={
          editing
            ? "옵션 이름과 기존 조합의 옵션 값은 바꿀 수 없습니다. 기존 조합은 가격만 바꾸고, 재고는 상품 상세에서 조정합니다."
            : "옵션(예: 색상, 사이즈)은 2개까지입니다. 값을 쉼표로 적고 조합을 만듭니다. 옵션이 없으면 한 줄만 둡니다."
        }
      >
        {editing ? (
          optionNames.length > 0 ? (
            <p className="text-sm">옵션: {optionNames.join(", ")}</p>
          ) : null
        ) : (
          <div className="grid gap-3">
            {optionNames.map((name, index) => (
              <div key={index} className="grid gap-3 sm:grid-cols-[12rem_1fr]">
                <TextField
                  name={`optionName${index}`}
                  label={`옵션 ${index + 1} 이름`}
                  maxLength={20}
                  value={name}
                  onChange={(event) => {
                    const value = event.currentTarget.value;
                    setOptionNames((current) =>
                      current.map((item, i) => (i === index ? value : item)),
                    );
                  }}
                  error={errors[`optionNames.${index}`]}
                />
                <TextField
                  name={`optionValues${index}`}
                  label="값(쉼표로 구분)"
                  placeholder="블랙, 화이트"
                  value={optionValues[index] ?? ""}
                  onChange={(event) => {
                    const value = event.currentTarget.value;
                    setOptionValues((current) =>
                      current.map((item, i) => (i === index ? value : item)),
                    );
                  }}
                />
              </div>
            ))}
            <div className="flex flex-wrap gap-2">
              {optionNames.length < PRODUCT_LIMITS.options ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setOptionCount(optionNames.length + 1)}
                >
                  옵션 추가
                </Button>
              ) : null}
              {optionNames.length > 0 ? (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setOptionCount(optionNames.length - 1)}
                  >
                    마지막 옵션 빼기
                  </Button>
                  <Button type="button" size="sm" onClick={generateRows}>
                    조합 만들기
                  </Button>
                </>
              ) : null}
            </div>
            {optionError ? <p className="text-xs text-destructive">{optionError}</p> : null}
            {errors.optionNames ? (
              <p className="text-xs text-destructive">{errors.optionNames}</p>
            ) : null}
          </div>
        )}

        {skus.length > 1 ? (
          <div className="flex flex-wrap items-end gap-2">
            <TextField
              name="bulkPrice"
              label="가격 일괄 입력"
              type="number"
              min={PRODUCT_LIMITS.minPrice}
              max={PRODUCT_LIMITS.maxPrice}
              step={10}
              value={bulkPrice}
              onChange={(event) => setBulkPrice(event.currentTarget.value)}
              className="w-40"
            />
            <TextField
              name="bulkStock"
              label={editing ? "새 조합 재고 일괄 입력" : "재고 일괄 입력"}
              type="number"
              min={0}
              max={PRODUCT_LIMITS.maxStock}
              value={bulkStock}
              onChange={(event) => setBulkStock(event.currentTarget.value)}
              className="w-40"
            />
            <Button type="button" variant="outline" size="sm" onClick={applyBulk}>
              모두 적용
            </Button>
          </div>
        ) : null}

        {skus.length === 0 ? (
          <p className="text-sm text-muted-foreground">옵션 값을 넣고 조합을 만들어 주세요.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  {optionNames.length > 0 ? <th className="p-2 font-medium">옵션</th> : null}
                  <th className="p-2 font-medium">가격(원)</th>
                  <th className="p-2 font-medium">
                    {editing ? "초기 재고(새 조합만)" : "초기 재고"}
                  </th>
                  <th className="p-2" />
                </tr>
              </thead>
              <tbody>
                {skus.map((row, index) => (
                  <tr key={row.key} className="border-b align-top">
                    {optionNames.length > 0 ? (
                      <td className="p-2">
                        {row.skuId || !editing ? (
                          <span>{row.optionValues.join(" / ")}</span>
                        ) : (
                          <div className="flex gap-1">
                            {optionNames.map((name, valueIndex) => (
                              <Input
                                key={name}
                                aria-label={`${index + 1}번째 줄 ${name}`}
                                required
                                maxLength={30}
                                className="w-28"
                                value={row.optionValues[valueIndex] ?? ""}
                                onChange={(event) => {
                                  const value = event.currentTarget.value;
                                  updateRow(row.key, {
                                    optionValues: optionNames.map((_, i) =>
                                      i === valueIndex ? value : (row.optionValues[i] ?? ""),
                                    ),
                                  });
                                }}
                              />
                            ))}
                          </div>
                        )}
                        <RowError message={errors[`skus.${index}.optionValues`]} />
                      </td>
                    ) : null}
                    <td className="p-2">
                      <Input
                        aria-label={`${index + 1}번째 줄 가격`}
                        type="number"
                        required
                        min={PRODUCT_LIMITS.minPrice}
                        max={PRODUCT_LIMITS.maxPrice}
                        step={10}
                        className="w-36"
                        value={row.price}
                        onChange={(event) =>
                          updateRow(row.key, { price: event.currentTarget.value })
                        }
                      />
                      <RowError message={errors[`skus.${index}.price`]} />
                    </td>
                    <td className="p-2">
                      {row.skuId ? (
                        <span className="text-muted-foreground">상세에서 조정</span>
                      ) : (
                        <>
                          <Input
                            aria-label={`${index + 1}번째 줄 초기 재고`}
                            type="number"
                            required
                            min={0}
                            max={PRODUCT_LIMITS.maxStock}
                            className="w-28"
                            value={row.initialStock}
                            onChange={(event) =>
                              updateRow(row.key, { initialStock: event.currentTarget.value })
                            }
                          />
                          <RowError message={errors[`skus.${index}.initialStock`]} />
                        </>
                      )}
                    </td>
                    <td className="p-2 text-right">
                      {!row.skuId && skus.length > 1 ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            setSkus((current) => current.filter((item) => item.key !== row.key))
                          }
                        >
                          빼기
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {editing && optionNames.length > 0 && skus.length < PRODUCT_LIMITS.skus ? (
          <div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setSkus((current) => [...current, newRow(optionNames.map(() => ""))])}
            >
              옵션 조합 추가
            </Button>
          </div>
        ) : null}
        {errors.skus ? <p className="text-xs text-destructive">{errors.skus}</p> : null}
      </Section>

      <div className="flex gap-2">
        <SubmitButton disabled={uploading || skus.length === 0}>{submitText}</SubmitButton>
        <Button asChild variant="outline">
          <Link href={hidden.productId ? `/products/${hidden.productId}` : "/products"}>취소</Link>
        </Button>
      </div>
    </form>
  );
}

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="grid gap-4 rounded-xl border bg-card p-6">
      <div className="grid gap-1">
        <h2 className="text-lg font-semibold">{title}</h2>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

function RowError({ message }: { message: string | undefined }) {
  return message ? <p className="mt-1 text-xs text-destructive">{message}</p> : null;
}
