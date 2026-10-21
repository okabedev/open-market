import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const put = vi.fn();
const del = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post, PUT: put, DELETE: del })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));
const revalidatePath = vi.fn();
vi.mock("next/cache", () => ({ revalidatePath: (path: string) => revalidatePath(path) }));

const { addToCart, changeCartItemQuantity, removeCartItem } = await import("./actions");

const SKU = "0199f000-0000-7000-8000-0000000000b1";
const PRODUCT = "0199f000-0000-7000-8000-0000000000a1";

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(values)) {
    data.set(name, value);
  }
  return data;
}

function ok(status = 204) {
  return { response: new Response(null, { status }) };
}

describe("addToCart", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
    revalidatePath.mockClear();
  });

  it("담으면 화면을 그릴 때 만든 멱등성 키를 붙여 보내고, 담았다는 안내와 함께 상품 화면으로 돌아간다", async () => {
    post.mockResolvedValue(ok());

    await expect(
      addToCart(
        idleState,
        form({ skuId: SKU, productId: PRODUCT, quantity: "2", idempotencyKey: "k-1" }),
      ),
    ).rejects.toThrow(`redirect:/products/${PRODUCT}?added=${SKU}`);

    expect(post).toHaveBeenCalledWith("/v1/cart/items", {
      body: { skuId: SKU, quantity: 2 },
      headers: { "Idempotency-Key": "k-1" },
    });
    expect(revalidatePath).toHaveBeenCalledWith("/cart");
  });

  it("수량이 1~99 정수가 아니면 order를 부르지 않고 필드 오류를 돌려준다", async () => {
    for (const quantity of ["0", "100", "1.5", "", "abc"]) {
      const state = await addToCart(
        idleState,
        form({ skuId: SKU, productId: PRODUCT, quantity, idempotencyKey: "k-1" }),
      );
      expect(state).toMatchObject({
        status: "error",
        fieldErrors: { quantity: expect.any(String) },
      });
    }
    expect(post).not.toHaveBeenCalled();
  });

  it("order가 거절하면(장바구니가 가득 참 등) 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "장바구니에는 50개까지 담을 수 있습니다.", code: "ORDER_CART_FULL" },
      response: new Response(null, { status: 422 }),
    });

    const state = await addToCart(
      idleState,
      form({ skuId: SKU, productId: PRODUCT, quantity: "1", idempotencyKey: "k-1" }),
    );

    expect(state).toMatchObject({
      status: "error",
      message: "장바구니에는 50개까지 담을 수 있습니다.",
    });
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("changeCartItemQuantity", () => {
  beforeEach(() => {
    put.mockReset();
    revalidatePath.mockClear();
  });

  it("수량을 바꾸고 장바구니를 다시 그린다", async () => {
    put.mockResolvedValue(ok());

    const state = await changeCartItemQuantity(idleState, form({ skuId: SKU, quantity: "3" }));

    expect(state.status).toBe("success");
    expect(put).toHaveBeenCalledWith("/v1/cart/items/{skuId}", {
      params: { path: { skuId: SKU } },
      body: { quantity: 3 },
    });
    expect(revalidatePath).toHaveBeenCalledWith("/cart");
  });

  it("수량이 틀리면 부르지 않고, 서비스가 거절하면 문구를 보여 준다", async () => {
    const invalid = await changeCartItemQuantity(idleState, form({ skuId: SKU, quantity: "0" }));
    expect(invalid).toMatchObject({
      status: "error",
      fieldErrors: { quantity: expect.any(String) },
    });
    expect(put).not.toHaveBeenCalled();

    put.mockResolvedValue({
      error: { detail: "장바구니에 없는 상품입니다.", code: "ORDER_CART_ITEM_NOT_FOUND" },
      response: new Response(null, { status: 404 }),
    });
    const rejected = await changeCartItemQuantity(idleState, form({ skuId: SKU, quantity: "2" }));
    expect(rejected).toMatchObject({ status: "error", message: "장바구니에 없는 상품입니다." });
  });
});

describe("removeCartItem", () => {
  it("빼고 장바구니를 다시 그린다", async () => {
    del.mockResolvedValue(ok());

    await removeCartItem(form({ skuId: SKU }));

    expect(del).toHaveBeenCalledWith("/v1/cart/items/{skuId}", {
      params: { path: { skuId: SKU } },
    });
    expect(revalidatePath).toHaveBeenCalledWith("/cart");
  });
});
