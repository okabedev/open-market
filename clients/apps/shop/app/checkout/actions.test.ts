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

const { placeOrder } = await import("./actions");

const SKU_A = "0199f000-0000-7000-8000-0000000000a1";
const SKU_B = "0199f000-0000-7000-8000-0000000000b2";
const ADDRESS = "0199f000-0000-7000-8000-00000000ad01";
const ORDER = "0199f000-0000-7000-8000-000000000001";

function form(addressId = ADDRESS): FormData {
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

  it("고른 배송지를 member에서 읽어 주문 본문에 넣고 멱등성 키를 붙인다. 만들면 결제 화면으로 간다", async () => {
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
      },
      headers: { "Idempotency-Key": "k-1" },
    });
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
