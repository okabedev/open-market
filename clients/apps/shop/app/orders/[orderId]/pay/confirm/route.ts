import { NextResponse } from "next/server";
import { bff } from "@/lib/bff";
import { confirmIdempotencyKey } from "@/lib/checkout";
import { currentSession } from "@/lib/session";

type ConfirmError = { code?: string; pgCode?: string; pgMessage?: string };

/**
 * 토스 결제위젯의 성공 주소(`successUrl`, ADR 0018). 토스가 넘긴 `paymentKey`, `orderId`, `amount`를 payment 승인 API에 그대로
 * 넘긴다(금액 대조는 payment가 한다). 결과에 따라 화면으로 보낸다.
 * - 승인됨(200)·확인 중(202): 완료 화면. 주문이 결제 완료가 될 때까지 거기서 기다린다
 * - 거절·주문 취소·그 밖의 오류: 결제 화면으로 돌아가 코드(토스 거절이면 토스 코드와 문구)를 보여 준다
 */
export async function GET(request: Request, { params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  const url = new URL(request.url);
  const back = (code: string, message?: string) => {
    const target = new URL(`/orders/${orderId}/pay`, url);
    target.searchParams.set("code", code);
    if (message) {
      target.searchParams.set("message", message);
    }
    return NextResponse.redirect(target, 303);
  };

  if (!(await currentSession())) {
    const login = new URL("/auth/login", url);
    login.searchParams.set("returnTo", `${url.pathname}${url.search}`);
    return NextResponse.redirect(login, 303);
  }
  const paymentKey = url.searchParams.get("paymentKey");
  const amount = Number(url.searchParams.get("amount"));
  if (url.searchParams.get("orderId") !== orderId || !paymentKey || !Number.isInteger(amount)) {
    return back("INVALID_REQUEST");
  }

  const payment = await bff.serviceClient("payment");
  const { error, response } = await payment.POST("/v1/payments/confirm", {
    body: { orderId, paymentKey, amount },
    headers: { "Idempotency-Key": confirmIdempotencyKey(paymentKey) },
  });
  if (response.ok) {
    return NextResponse.redirect(new URL(`/orders/${orderId}/complete`, url), 303);
  }
  const problem = (error ?? {}) as ConfirmError;
  if (problem.code === "PAYMENT_REJECTED") {
    return back(problem.pgCode ?? "PAYMENT_REJECTED", problem.pgMessage);
  }
  return back(response.status >= 500 ? "UNAVAILABLE" : (problem.code ?? "UNAVAILABLE"));
}
