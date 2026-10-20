import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const put = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post, PUT: put })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { adjustStock, registerProduct, stopProduct, updateProduct } = await import("./actions");

const PRODUCT = "0199f5a0-0000-7000-8000-0000000000b1";
const SKU = "0199f5a0-0000-7000-8000-0000000000c1";
const KEY = "0199f5a0-0000-7000-8000-0000000000d1";

function form(fields: Record<string, unknown>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, typeof value === "string" ? value : JSON.stringify(value));
  }
  return data;
}

const PRODUCT_FIELDS = {
  categoryId: "0199f000-0000-7000-8000-000000000101",
  name: "반팔 티셔츠",
  description: "면 100%",
  imageFileIds: ["0199f5a0-0000-7000-8000-0000000000f1"],
  optionNames: ["사이즈"],
  skus: [{ optionValues: ["M"], price: 19_900, initialStock: 5 }],
};

describe("registerProduct", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("규칙에 맞지 않으면 catalog를 부르지 않는다", async () => {
    const state = await registerProduct(
      idleState,
      form({ ...PRODUCT_FIELDS, name: "", idempotencyKey: KEY }),
    );

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.name).toBe("상품 이름을 입력해 주세요.");
  });

  it("멱등성 키를 붙여 등록하고 상품 상세로 보낸다", async () => {
    post.mockResolvedValue({
      data: { productId: PRODUCT },
      response: new Response(null, { status: 201 }),
    });

    await expect(
      registerProduct(idleState, form({ ...PRODUCT_FIELDS, idempotencyKey: KEY })),
    ).rejects.toThrow(`redirect:/products/${PRODUCT}?notice=registered`);

    expect(post).toHaveBeenCalledWith("/v1/seller/products", {
      headers: { "Idempotency-Key": KEY },
      body: PRODUCT_FIELDS,
    });
  });

  it("catalog가 거절하면 문구를 보여 주고 입력값을 돌려준다", async () => {
    post.mockResolvedValue({
      error: {
        detail: "판매자 정보가 아직 준비되지 않았습니다.",
        code: "CATALOG_SELLER_NOT_READY",
      },
      response: new Response(null, { status: 409 }),
    });

    const state = await registerProduct(
      idleState,
      form({ ...PRODUCT_FIELDS, idempotencyKey: KEY }),
    );

    expect(state).toMatchObject({
      status: "error",
      message: "판매자 정보가 아직 준비되지 않았습니다.",
    });
    expect(state.values?.name).toBe("반팔 티셔츠");
  });
});

describe("updateProduct", () => {
  beforeEach(() => {
    put.mockReset();
    redirect.mockClear();
  });

  it("옵션 이름은 보내지 않고, 기존 SKU는 ID와 가격만, 새 SKU는 초기 재고와 함께 보낸다", async () => {
    put.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      updateProduct(
        idleState,
        form({
          ...PRODUCT_FIELDS,
          productId: PRODUCT,
          skus: [
            { skuId: SKU, optionValues: ["M"], price: 18_900, initialStock: 0 },
            { optionValues: ["L"], price: 19_900, initialStock: 3 },
          ],
        }),
      ),
    ).rejects.toThrow(`redirect:/products/${PRODUCT}?notice=updated`);

    expect(put).toHaveBeenCalledWith("/v1/seller/products/{productId}", {
      params: { path: { productId: PRODUCT } },
      body: {
        categoryId: PRODUCT_FIELDS.categoryId,
        name: "반팔 티셔츠",
        description: "면 100%",
        imageFileIds: PRODUCT_FIELDS.imageFileIds,
        skus: [
          { skuId: SKU, optionValues: ["M"], price: 18_900 },
          { optionValues: ["L"], price: 19_900, initialStock: 3 },
        ],
      },
    });
  });
});

describe("stopProduct", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("상품 ID가 UUID가 아니면 부르지 않는다", async () => {
    const state = await stopProduct(idleState, form({ productId: "../admin" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.status).toBe("error");
  });

  it("판매 중지가 거절되면 문구를 그 자리에 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "등록 중인 상품은 멈출 수 없습니다." },
      response: new Response(null, { status: 422 }),
    });

    const state = await stopProduct(idleState, form({ productId: PRODUCT }));

    expect(state).toMatchObject({ status: "error", message: "등록 중인 상품은 멈출 수 없습니다." });
  });
});

describe("adjustStock", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("멱등성 키를 붙여 증감 수량과 사유를 보낸다. 빈 사유는 보내지 않는다", async () => {
    post.mockResolvedValue({ data: {}, response: new Response(null, { status: 201 }) });

    await expect(
      adjustStock(
        idleState,
        form({ productId: PRODUCT, skuId: SKU, idempotencyKey: KEY, delta: "-2", reason: " " }),
      ),
    ).rejects.toThrow(`redirect:/products/${PRODUCT}?notice=stock-adjusted`);

    expect(post).toHaveBeenCalledWith(
      "/v1/seller/products/{productId}/skus/{skuId}/stock-adjustments",
      {
        params: { path: { productId: PRODUCT, skuId: SKU } },
        headers: { "Idempotency-Key": KEY },
        body: { delta: -2 },
      },
    );
  });

  it("0은 보내지 않고 필드 오류를 준다", async () => {
    const state = await adjustStock(
      idleState,
      form({ productId: PRODUCT, skuId: SKU, idempotencyKey: KEY, delta: "0", reason: "" }),
    );

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.delta).toBe("0이 아닌 수량을 입력해 주세요.");
  });
});
