"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { productPath, type Notice } from "@/lib/notice";
import { readProductForm, stockAdjustmentSchema } from "@/lib/product-form";

const uuid = z.uuid();

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}

/** 폼의 상품 ID. 화면이 넣은 값이지만 경로에 쓰므로 UUID인지 본다. */
function productId(form: FormData): string | null {
  const parsed = uuid.safeParse(text(form, "productId"));
  return parsed.success ? parsed.data : null;
}

const INVALID_TARGET: FormState = { status: "error", message: "상품을 찾을 수 없습니다." };

/**
 * 상품을 등록한다(CAT-1). 화면을 그릴 때 만든 멱등성 키(`idempotencyKey`)를 보내서 두 번 눌러도 하나만 생긴다. 성공하면 상품 상세로
 * 간다(상태는 등록 중).
 */
export async function registerProduct(_: FormState, form: FormData): Promise<FormState> {
  const result = readProductForm(form);
  if (!result.success) {
    return result.state;
  }
  const catalog = await bff.serviceClient("catalog");
  const { data, error, response } = await catalog.POST("/v1/seller/products", {
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
    body: result.data,
  });
  if (!response.ok || !data?.productId) {
    return problemState(error, response.status, valuesOf(form));
  }
  redirect(productPath(data.productId, "registered"));
}

/**
 * 상품을 고친다(CAT-2). 옵션 이름은 바꿀 수 없어 보내지 않는다. 기존 SKU는 가격만 바뀌고, ID 없는 줄은 새 SKU로 초기 재고와 함께
 * 보낸다.
 */
export async function updateProduct(_: FormState, form: FormData): Promise<FormState> {
  const id = productId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const result = readProductForm(form);
  if (!result.success) {
    return result.state;
  }
  const { categoryId, name, description, imageFileIds, skus } = result.data;
  const catalog = await bff.serviceClient("catalog");
  const { error, response } = await catalog.PUT("/v1/seller/products/{productId}", {
    params: { path: { productId: id } },
    body: {
      categoryId,
      name,
      description,
      imageFileIds,
      skus: skus.map(({ skuId, optionValues, price, initialStock }) =>
        skuId ? { skuId, optionValues, price } : { optionValues, price, initialStock },
      ),
    },
  });
  if (!response.ok) {
    return problemState(error, response.status, valuesOf(form));
  }
  redirect(productPath(id, "updated"));
}

type StatusPath =
  | "/v1/seller/products/{productId}/publish"
  | "/v1/seller/products/{productId}/stop"
  | "/v1/seller/products/{productId}/resume";

async function changeStatus(form: FormData, path: StatusPath, notice: Notice): Promise<FormState> {
  const id = productId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const catalog = await bff.serviceClient("catalog");
  const { error, response } = await catalog.POST(path, { params: { path: { productId: id } } });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(productPath(id, notice));
}

/** 판매를 시작한다. 이미지가 없으면 422 `CATALOG_PRODUCT_NOT_PUBLISHABLE`의 문구를 보여 준다. */
export async function publishProduct(_: FormState, form: FormData): Promise<FormState> {
  return changeStatus(form, "/v1/seller/products/{productId}/publish", "published");
}

/** 판매를 멈춘다(CAT-2). 검색에서 몇 초 안에 빠진다. */
export async function stopProduct(_: FormState, form: FormData): Promise<FormState> {
  return changeStatus(form, "/v1/seller/products/{productId}/stop", "stopped");
}

/** 직접 멈춘 상품의 판매를 다시 연다. */
export async function resumeProduct(_: FormState, form: FormData): Promise<FormState> {
  return changeStatus(form, "/v1/seller/products/{productId}/resume", "resumed");
}

/**
 * SKU 재고를 조정한다(CAT-3). 화면을 그릴 때 만든 멱등성 키를 보내서 두 번 눌러도 한 번만 반영된다. 조정 뒤 재고가 예약 수량보다 작으면
 * 422 문구를 보여 준다.
 */
export async function adjustStock(_: FormState, form: FormData): Promise<FormState> {
  const id = productId(form);
  const sku = uuid.safeParse(text(form, "skuId"));
  if (!id || !sku.success) {
    return INVALID_TARGET;
  }
  const values = { delta: text(form, "delta"), reason: text(form, "reason") };
  const parsed = stockAdjustmentSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const { delta, reason } = parsed.data;
  const catalog = await bff.serviceClient("catalog");
  const { error, response } = await catalog.POST(
    "/v1/seller/products/{productId}/skus/{skuId}/stock-adjustments",
    {
      params: { path: { productId: id, skuId: sku.data } },
      headers: { "Idempotency-Key": text(form, "idempotencyKey") },
      body: reason ? { delta, reason } : { delta },
    },
  );
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect(productPath(id, "stock-adjusted"));
}

/** 실패한 상품 폼에 다시 채울 값. */
function valuesOf(form: FormData): Record<string, string> {
  return Object.fromEntries(
    ["categoryId", "name", "description", "imageFileIds", "optionNames", "skus"].map((name) => [
      name,
      text(form, name),
    ]),
  );
}
