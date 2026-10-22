import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { cancelSellerOrder, confirmPurchase, requestClaim, requestMediation } =
  await import("./actions");

const ORDER = "0199f5a0-0000-7000-8000-0000000000a1";
const SELLER_ORDER = "0199f5a0-0000-7000-8000-0000000000b1";
const CLAIM = "0199f5a0-0000-7000-8000-0000000000c1";
const PHOTO = "0199f5a0-0000-7000-8000-0000000000f1";
const PAGE = `/orders/${ORDER}/seller-orders/${SELLER_ORDER}`;

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

const target = { orderId: ORDER, sellerOrderId: SELLER_ORDER };
const ok = (status = 204) => ({ response: new Response(null, { status }) });

beforeEach(() => {
  post.mockReset();
  redirect.mockClear();
});

describe("cancelSellerOrder", () => {
  it("발송 전 하위 주문을 취소하고 배송 조회로 돌아간다", async () => {
    post.mockResolvedValue(ok());

    await expect(cancelSellerOrder(idleState, form(target))).rejects.toThrow(
      `redirect:${PAGE}?notice=cancelled`,
    );
    expect(post).toHaveBeenCalledWith("/v1/seller-orders/{sellerOrderId}/cancel", {
      params: { path: { sellerOrderId: SELLER_ORDER } },
    });
  });

  it("그사이 발송됐으면 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "이미 발송한 주문입니다." },
      response: new Response(null, { status: 409 }),
    });

    const state = await cancelSellerOrder(idleState, form(target));

    expect(state).toMatchObject({ status: "error", message: "이미 발송한 주문입니다." });
  });

  it("ID가 아니면 부르지 않는다", async () => {
    const state = await cancelSellerOrder(idleState, form({ ...target, sellerOrderId: "x/../y" }));

    expect(state.status).toBe("error");
    expect(post).not.toHaveBeenCalled();
  });
});

describe("confirmPurchase", () => {
  it("구매확정하고 배송 조회로 돌아간다", async () => {
    post.mockResolvedValue(ok());

    await expect(confirmPurchase(idleState, form(target))).rejects.toThrow(
      `redirect:${PAGE}?notice=confirmed`,
    );
    expect(post).toHaveBeenCalledWith("/v1/seller-orders/{sellerOrderId}/confirm", {
      params: { path: { sellerOrderId: SELLER_ORDER } },
    });
  });
});

describe("requestClaim", () => {
  const fields = {
    ...target,
    type: "RETURN",
    reason: "CHANGE_OF_MIND",
    description: "  사이즈가 맞지 않아요 ",
    fileIds: JSON.stringify([PHOTO]),
    idempotencyKey: "k-1",
  };

  it("멱등성 키를 붙여 신청하고 배송 조회로 돌아간다. 설명의 앞뒤 공백은 지운다", async () => {
    post.mockResolvedValue({ data: { claimId: CLAIM }, ...ok(201) });

    await expect(requestClaim(idleState, form(fields))).rejects.toThrow(
      `redirect:${PAGE}?notice=claim-requested`,
    );
    expect(post).toHaveBeenCalledWith("/v1/seller-orders/{sellerOrderId}/claims", {
      params: { path: { sellerOrderId: SELLER_ORDER } },
      headers: { "Idempotency-Key": "k-1" },
      body: {
        type: "RETURN",
        reason: "CHANGE_OF_MIND",
        description: "사이즈가 맞지 않아요",
        fileIds: [PHOTO],
      },
    });
  });

  it("교환은 판매자 귀책 사유만, 설명은 1~500자, 사진은 5장까지다", async () => {
    const state = await requestClaim(
      idleState,
      form({
        ...fields,
        type: "EXCHANGE",
        description: " ",
        fileIds: JSON.stringify(
          Array.from({ length: 6 }, (_, index) => `${PHOTO.slice(0, -1)}${index}`),
        ),
      }),
    );

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors).toEqual({
      reason: "교환은 상품 불량, 오배송, 상품 정보와 다름일 때만 신청할 수 있습니다.",
      description: "사유를 자세히 적어 주세요.",
      fileIds: "사진은 5장까지 올릴 수 있습니다.",
    });
    expect(state.values).toMatchObject({ type: "EXCHANGE", reason: "CHANGE_OF_MIND" });
  });

  it("거절되면 입력을 남기고 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "이미 진행 중인 클레임이 있습니다." },
      response: new Response(null, { status: 409 }),
    });

    const state = await requestClaim(idleState, form(fields));

    expect(state).toMatchObject({
      status: "error",
      message: "이미 진행 중인 클레임이 있습니다.",
      values: { description: "  사이즈가 맞지 않아요 ", fileIds: JSON.stringify([PHOTO]) },
    });
  });
});

describe("requestMediation", () => {
  it("거부된 클레임의 중재를 요청한다", async () => {
    post.mockResolvedValue(ok());

    await expect(requestMediation(idleState, form({ ...target, claimId: CLAIM }))).rejects.toThrow(
      `redirect:${PAGE}?notice=mediation-requested`,
    );
    expect(post).toHaveBeenCalledWith("/v1/claims/{claimId}/mediation", {
      params: { path: { claimId: CLAIM } },
    });
  });

  it("기한이 지났으면 서비스 문구를 보여 준다", async () => {
    post.mockResolvedValue({
      error: { detail: "중재를 요청할 수 있는 기한이 지났습니다." },
      response: new Response(null, { status: 409 }),
    });

    const state = await requestMediation(idleState, form({ ...target, claimId: CLAIM }));

    expect(state.message).toBe("중재를 요청할 수 있는 기한이 지났습니다.");
  });
});
