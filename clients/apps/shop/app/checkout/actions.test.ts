import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const getAddresses = vi.fn();
const postOrder = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: {
    serviceClient: vi.fn(async (service: string) =>
      service === "member" ? { GET: getAddresses } : { POST: postOrder },
    ),
  },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));
const revalidatePath = vi.fn();
vi.mock("next/cache", () => ({ revalidatePath: (path: string) => revalidatePath(path) }));

const { buyNow, placeOrder } = await import("./actions");

const SKU_A = "0199f000-0000-7000-8000-0000000000a1";
const SKU_B = "0199f000-0000-7000-8000-0000000000b2";
const ADDRESS = "0199f000-0000-7000-8000-00000000ad01";
const ORDER = "0199f000-0000-7000-8000-000000000001";

const COUPON_P = "0199f000-0000-7000-8000-0000000c0001";
const COUPON_A = "0199f000-0000-7000-8000-0000000c00a1";

function form(addressId = ADDRESS, coupons: string[] = [], expectedAmount = "79700"): FormData {
  const data = new FormData();
  for (const [skuId, quantity, price] of [
    [SKU_A, "2", "19900"],
    [SKU_B, "1", "39900"],
  ]) {
    data.append("skuId", skuId!);
    data.append("quantity", quantity!);
    data.append("expectedPrice", price!);
  }
  data.set("addressId", addressId);
  data.set("idempotencyKey", "k-1");
  data.set("expectedAmount", expectedAmount);
  for (const coupon of coupons) {
    data.append("issuedCouponId", coupon);
  }
  return data;
}

const address = {
  addressId: ADDRESS,
  recipientName: "홍길동",
  recipientPhone: "010-1234-5678",
  zipCode: "06236",
  baseAddress: "서울 강남구 테헤란로 1",
  detailAddress: "101호",
};

describe("placeOrder", () => {
  beforeEach(() => {
    getAddresses.mockReset();
    postOrder.mockReset();
    redirect.mockClear();
    revalidatePath.mockClear();
    getAddresses.mockResolvedValue({
      data: [address],
      response: new Response(null, { status: 200 }),
    });
  });

  it("고른 배송지를 member에서 읽어 주문 본문에 넣고, 보여 준 결제 금액과 멱등성 키를 붙인다. 만들면 결제 화면으로 간다", async () => {
    postOrder.mockResolvedValue({
      data: { orderId: ORDER },
      response: new Response(null, { status: 201 }),
    });

    await expect(placeOrder(idleState, form())).rejects.toThrow(`redirect:/orders/${ORDER}/pay`);

    expect(postOrder).toHaveBeenCalledWith("/v1/orders", {
      body: {
        lines: [
          { skuId: SKU_A, quantity: 2, expectedPrice: 19_900 },
          { skuId: SKU_B, quantity: 1, expectedPrice: 39_900 },
        ],
        shipping: {
          recipientName: "홍길동",
          recipientPhone: "010-1234-5678",
          zipCode: "06236",
          baseAddress: "서울 강남구 테헤란로 1",
          detailAddress: "101호",
        },
        expectedAmount: 79_700,
      },
      headers: { "Idempotency-Key": "k-1" },
    });
  });

  it("고른 쿠폰과 할인 뒤 결제 금액을 함께 보낸다", async () => {
    postOrder.mockResolvedValue({
      data: { orderId: ORDER },
      response: new Response(null, { status: 201 }),
    });

    await expect(
      placeOrder(idleState, form(ADDRESS, [COUPON_P, COUPON_A], "74000")),
    ).rejects.toThrow(`redirect:/orders/${ORDER}/pay`);

    expect(postOrder.mock.calls[0]?.[1].body).toMatchObject({
      issuedCouponIds: [COUPON_P, COUPON_A],
      expectedAmount: 74_000,
    });
  });

  it("그사이 쿠폰을 쓸 수 없게 됐거나 금액이 바뀌면 서비스 문구를 보여 주고 주문서를 새로 그린다", async () => {
    for (const [status, code] of [
      [422, "ORDER_COUPON_NOT_APPLICABLE"],
      [409, "ORDER_AMOUNT_CHANGED"],
    ] as const) {
      postOrder.mockResolvedValueOnce({
        error: { detail: "쿠폰을 쓸 수 없습니다.", code },
        response: new Response(null, { status }),
      });
      const state = await placeOrder(idleState, form(ADDRESS, [COUPON_P], "74000"));
      expect(state).toMatchObject({ status: "error", message: "쿠폰을 쓸 수 없습니다." });
    }
    expect(revalidatePath).toHaveBeenCalledWith("/checkout");
  });

  it("배송지를 고르지 않았거나 없는 배송지면 주문하지 않는다", async () => {
    const none = await placeOrder(idleState, form(""));
    expect(none).toMatchObject({ status: "error", fieldErrors: { addressId: expect.any(String) } });

    const gone = await placeOrder(idleState, form("0199f000-0000-7000-8000-00000000ad99"));
    expect(gone).toMatchObject({ status: "error", fieldErrors: { addressId: expect.any(String) } });
    expect(postOrder).not.toHaveBeenCalled();
  });

  it("가격이 바뀌었거나 재고가 모자라면 서비스 문구를 보여 주고 주문서를 새로 그린다(새 가격과 새 키)", async () => {
    postOrder.mockResolvedValue({
      error: { detail: "가격이 바뀐 상품이 있습니다.", code: "ORDER_PRICE_CHANGED" },
      response: new Response(null, { status: 409 }),
    });

    const state = await placeOrder(idleState, form());

    expect(state).toMatchObject({ status: "error", message: "가격이 바뀐 상품이 있습니다." });
    expect(revalidatePath).toHaveBeenCalledWith("/checkout");
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("buyNow", () => {
  it("상품 상세의 SKU와 수량으로 바로 구매 주문서를 연다. 수량은 주문서가 확인한다", async () => {
    const data = new FormData();
    data.set("skuId", SKU_A);
    data.set("productId", "0199f000-0000-7000-8000-0000000000f1");
    data.set("quantity", " 3 ");

    await expect(buyNow(data)).rejects.toThrow(
      `redirect:/checkout?buyNow=${SKU_A}&productId=0199f000-0000-7000-8000-0000000000f1&quantity=3`,
    );
  });
});
