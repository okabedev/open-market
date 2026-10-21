"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type AutoRefreshProps = {
  /** 다시 그리는 간격(밀리초) */
  intervalMs?: number;
  /** 최대 횟수. 넘으면 멈추고 새로 고침을 안내한다 */
  maxTimes?: number;
};

/**
 * 서버 컴포넌트를 주기적으로 다시 그린다. 주문 상태가 이벤트로 바뀌기를 기다리는 화면(재고 확인, 결제 확인)이 쓴다. 브라우저는 gateway를
 * 부르지 않고 같은 화면을 다시 받는다.
 */
export function AutoRefresh({ intervalMs = 1_500, maxTimes = 40 }: AutoRefreshProps) {
  const router = useRouter();
  const [times, setTimes] = useState(0);
  const done = times >= maxTimes;
  useEffect(() => {
    if (done) {
      return;
    }
    const timer = setTimeout(() => {
      router.refresh();
      setTimes((count) => count + 1);
    }, intervalMs);
    return () => clearTimeout(timer);
  }, [done, intervalMs, router, times]);
  return done ? (
    <p className="text-sm text-muted-foreground">
      시간이 걸리고 있습니다. 잠시 뒤 화면을 새로 고쳐 주세요.
    </p>
  ) : null;
}
