import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const session = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post })) },
}));
vi.mock("@/lib/session", () => ({ currentSession: () => session() }));

const { GET } = await import("./route");
const { confirmIdempotencyKey } = await import("@/lib/checkout");

const ORDER = "0199f000-0000-7000-8000-000000000001";
const BASE = `http://localhost:3000/orders/${ORDER}/pay`;

function request(query: string) {
  return new Request(`${BASE}/confirm?${query}`);
}

async function call(query: string) {
  const response = await GET(request(query), { params: Promise.resolve({ orderId: ORDER }) });
  return { status: response.status, location: response.headers.get("location") };
}

function result(status: number, body: unknown) {
  return status < 300
    ? { data: body, response: new Response(null, { status }) }
    : { error: body, response: new Response(null, { status }) };
}

const TOSS = `paymentType=NORMAL&orderId=${ORDER}&paymentKey=tgen_abc&amount=59700`;

describe("결제 승인(successUrl)", () => {
  beforeEach(() => {
    post.mockReset();
    session.mockReset();
    session.mockResolvedValue({ roles: [] });
  });

  it("토스가 넘긴 값을 그대로 payment 승인에 보내고, 결제 키로 만든 멱등성 키를 붙인다. 승인되면 완료 화면이다", async () => {
    post.mockResolvedValue(result(200, { status: "APPROVED" }));

    expect(await call(TOSS)).toEqual({
      status: 303,
      location: `http://localhost:3000/orders/${ORDER}/complete`,
    });
    expect(post).toHaveBeenCalledWith("/v1/payments/confirm", {
      body: { orderId: ORDER, paymentKey: "tgen_abc", amount: 59_700 },
      headers: { "Idempotency-Key": confirmIdempotencyKey("tgen_abc") },
    });
  });

  it("결과를 아직 모르면(202) 완료 화면에서 기다린다", async () => {
    post.mockResolvedValue(result(202, { status: "IN_PROGRESS" }));

    expect((await call(TOSS)).location).toBe(`http://localhost:3000/orders/${ORDER}/complete`);
  });

  it("토스가 거절하면 토스 코드와 문구를 들고 결제 화면으로 돌아간다", async () => {
    post.mockResolvedValue(
      result(422, {
        code: "PAYMENT_REJECTED",
        pgCode: "REJECT_CARD_PAYMENT",
        pgMessage: "잔액 부족",
      }),
    );

    const { location } = await call(TOSS);

    const url = new URL(location!);
    expect(url.pathname).toBe(`/orders/${ORDER}/pay`);
    expect(url.searchParams.get("code")).toBe("REJECT_CARD_PAYMENT");
    expect(url.searchParams.get("message")).toBe("잔액 부족");
  });

  it("주문이 취소됐거나 그 밖의 오류면 우리 코드를 들고 결제 화면으로 돌아간다", async () => {
    post.mockResolvedValue(result(409, { code: "PAYMENT_CANCELLED" }));
    expect(new URL((await call(TOSS)).location!).searchParams.get("code")).toBe(
      "PAYMENT_CANCELLED",
    );

    post.mockResolvedValue(result(503, undefined));
    expect(new URL((await call(TOSS)).location!).searchParams.get("code")).toBe("UNAVAILABLE");
  });

  it("주문 번호가 경로와 다르거나 값이 빠졌으면 승인하지 않는다", async () => {
    const other = await call(`orderId=0199f000-0000-7000-8000-000000000002&paymentKey=k&amount=1`);
    expect(new URL(other.location!).searchParams.get("code")).toBe("INVALID_REQUEST");

    const missing = await call(`orderId=${ORDER}&amount=1`);
    expect(new URL(missing.location!).searchParams.get("code")).toBe("INVALID_REQUEST");
    expect(post).not.toHaveBeenCalled();
  });

  it("세션이 끝났으면 로그인한 뒤 같은 승인 주소로 돌아온다", async () => {
    session.mockResolvedValue(null);

    const { location } = await call(TOSS);

    const url = new URL(location!);
    expect(url.pathname).toBe("/auth/login");
    expect(url.searchParams.get("returnTo")).toBe(`/orders/${ORDER}/pay/confirm?${TOSS}`);
    expect(post).not.toHaveBeenCalled();
  });
});
