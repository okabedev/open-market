"use client";

import { Alert, Button } from "@openmarket/ui";
import {
  ANONYMOUS,
  loadTossPayments,
  type TossPaymentsWidgets,
} from "@tosspayments/tosspayments-sdk";
import { useEffect, useRef, useState } from "react";

type PaymentWidgetProps = {
  /** 토스 클라이언트 키. 공개 값이다(ADR 0018) */
  clientKey: string;
  /** SDK 스크립트 주소. 없으면 토스 기본 주소다. E2E는 가짜 PG 주소를 준다(ADR 0018) */
  sdkUrl?: string;
  orderId: string;
  orderName: string;
  amount: number;
};

/**
 * 토스페이먼츠 결제위젯(SDK v2, ADR 0018). 결제수단과 약관을 그리고, 결제하기를 누르면 토스 인증 화면으로 넘어간다(Redirect 방식). 성공하면
 * `/orders/<id>/pay/confirm`(BFF가 승인), 실패하면 이 화면으로 코드와 문구를 들고 돌아온다. 구매자 식별자는 넘기지 않는다(`ANONYMOUS`).
 */
export function PaymentWidget({
  clientKey,
  sdkUrl,
  orderId,
  orderName,
  amount,
}: PaymentWidgetProps) {
  const widgets = useRef<TossPaymentsWidgets | null>(null);
  // 위젯은 화면에 한 번만 그린다. 개발 모드(StrictMode)는 effect를 두 번 부르는데, 두 번째 렌더링은 토스가 거절한다.
  const initializing = useRef<Promise<TossPaymentsWidgets> | null>(null);
  const [ready, setReady] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    initializing.current ??= (async () => {
      const toss = await loadTossPayments(clientKey, sdkUrl ? { src: sdkUrl } : {});
      const created = toss.widgets({ customerKey: ANONYMOUS });
      await created.setAmount({ currency: "KRW", value: amount });
      await Promise.all([
        created.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" }),
        created.renderAgreement({ selector: "#payment-agreement", variantKey: "AGREEMENT" }),
      ]);
      return created;
    })();
    initializing.current.then(
      (created) => {
        if (!cancelled) {
          widgets.current = created;
          setReady(true);
        }
      },
      (cause: unknown) => {
        console.error("토스 결제위젯을 그리지 못했다", cause);
        if (!cancelled) {
          setError("결제 화면을 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [clientKey, sdkUrl, amount]);

  async function pay() {
    if (!widgets.current) {
      return;
    }
    setRequesting(true);
    setError(null);
    try {
      await widgets.current.requestPayment({
        orderId,
        orderName,
        successUrl: `${window.location.origin}/orders/${orderId}/pay/confirm`,
        failUrl: `${window.location.origin}/orders/${orderId}/pay`,
      });
    } catch (cause) {
      // 결제수단을 고르지 않았거나 약관에 동의하지 않은 경우 등. 토스 SDK의 문구를 보여 준다.
      setError(
        cause instanceof Error && cause.message
          ? cause.message
          : "결제를 요청하지 못했습니다. 다시 시도해 주세요.",
      );
      setRequesting(false);
    }
  }

  return (
    <div className="grid gap-4">
      <div id="payment-method" />
      <div id="payment-agreement" />
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      <Button type="button" size="lg" disabled={!ready || requesting} onClick={pay}>
        {requesting ? "결제 화면으로 이동 중…" : "결제하기"}
      </Button>
    </div>
  );
}
