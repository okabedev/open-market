// 가짜 PG(ADR 0018). 마일스톤 E2E에서 토스페이먼츠 대신 쓴다. Node 표준 라이브러리만 쓰고 상태는 메모리에 둔다.
// - 브라우저: SDK v2 스크립트(GET /v2/standard)와 결제 인증 화면(GET /checkout → 승인·취소 버튼)
// - payment 서비스: 토스 API 승인·조회·취소(/v1/payments/...). 응답 형식은 payment가 읽는 필드를 실제 토스 응답에 맞췄다
// - 시나리오: 제어 API(/control/...)로 주문별 승인 지연과 승인·취소 거절을 정하고, 승인·취소 요청을 확인한다
// 사용법은 docs/dev-setup.md "E2E". 테스트는 레포 루트에서 `node --test infra/fake-pg/*.test.mjs`.
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { createServer } from "node:http";

const SDK = readFileSync(new URL("./sdk.js", import.meta.url));

/** 가짜 PG HTTP 서버를 만든다. 듣기(listen)는 부르는 쪽이 한다. */
export function createFakePg() {
  /** paymentKey → 토스 결제 객체 */
  let payments = new Map();
  /** orderId → { confirmDelayMs, confirmError } */
  let settings = new Map();
  /** orderId → { confirmRequests, cancelRequests } */
  let counts = new Map();
  /** `<종류>:<Idempotency-Key>` → 처리 중이면 null, 끝났으면 { status, body } */
  let idempotent = new Map();

  function countOf(orderId) {
    if (!counts.has(orderId)) {
      counts.set(orderId, { confirmRequests: 0, cancelRequests: 0 });
    }
    return counts.get(orderId);
  }

  const routes = [
    ["GET", /^\/health$/, () => json(200, { status: "UP" })],
    ["GET", /^\/v2\/standard$/, () => ({ status: 200, type: "text/javascript; charset=utf-8", body: SDK })],
    ["GET", /^\/checkout$/, (request, url) => checkoutPage(url.searchParams)],
    ["POST", /^\/checkout\/approve$/, async (request) => approve(await form(request))],
    ["POST", /^\/checkout\/cancel$/, async (request) => cancelCheckout(await form(request))],
    ["POST", /^\/v1\/payments\/confirm$/, async (request) => confirm(request, await body(request))],
    ["GET", /^\/v1\/payments\/([^/]+)$/, (request, url, [key]) => find(request, key)],
    ["POST", /^\/v1\/payments\/([^/]+)\/cancel$/, async (request, url, [key]) => cancel(request, key, await body(request))],
    ["PUT", /^\/control\/orders\/([^/]+)$/, async (request, url, [orderId]) => configure(orderId, await body(request))],
    ["GET", /^\/control\/orders\/([^/]+)$/, (request, url, [orderId]) => inspect(orderId)],
    ["DELETE", /^\/control$/, () => reset()],
  ];

  function approve(fields) {
    const amount = Number(fields.get("amount"));
    const paymentKey = `fake_${randomUUID().replaceAll("-", "")}`;
    payments.set(paymentKey, {
      mId: "fake-pg",
      paymentKey,
      orderId: fields.get("orderId"),
      orderName: fields.get("orderName"),
      type: "NORMAL",
      method: "카드",
      currency: "KRW",
      status: "READY",
      totalAmount: amount,
      balanceAmount: amount,
      requestedAt: kst(new Date()),
      approvedAt: null,
      lastTransactionKey: null,
      cancels: null,
    });
    const success = new URL(fields.get("successUrl"));
    success.searchParams.set("paymentType", "NORMAL");
    success.searchParams.set("orderId", fields.get("orderId"));
    success.searchParams.set("paymentKey", paymentKey);
    success.searchParams.set("amount", String(amount));
    return redirect(success);
  }

  function cancelCheckout(fields) {
    const fail = new URL(fields.get("failUrl"));
    fail.searchParams.set("code", "PAY_PROCESS_CANCELED");
    fail.searchParams.set("message", "사용자에 의해 결제가 취소되었습니다");
    fail.searchParams.set("orderId", fields.get("orderId"));
    return redirect(fail);
  }

  async function confirm(request, { paymentKey, orderId, amount }) {
    if (!authorized(request)) {
      return unauthorized();
    }
    if (orderId) {
      countOf(orderId).confirmRequests += 1;
    }
    return once(request, "confirm", async () => {
      const payment = payments.get(paymentKey);
      if (!payment) {
        return error(404, "NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료되어 결제 진행 데이터가 존재하지 않습니다.");
      }
      if (payment.orderId !== orderId || payment.totalAmount !== amount) {
        return error(400, "INVALID_REQUEST", "결제 인증 정보와 요청한 주문 번호나 금액이 다릅니다.");
      }
      if (payment.status !== "READY") {
        return error(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.");
      }
      const { confirmDelayMs = 0, confirmError } = settings.get(orderId) ?? {};
      let response;
      if (confirmError) {
        payment.status = "ABORTED";
        response = error(confirmError.status, confirmError.code, confirmError.message);
      } else {
        // 토스에서는 승인이 끝났고 응답만 늦게 온다. 그 사이 조회하면 DONE이다.
        payment.status = "DONE";
        payment.approvedAt = kst(new Date());
        payment.lastTransactionKey = transactionKey();
        response = json(200, payment);
      }
      await new Promise((resolve) => setTimeout(resolve, confirmDelayMs));
      return response;
    });
  }

  function find(request, paymentKey) {
    if (!authorized(request)) {
      return unauthorized();
    }
    const payment = payments.get(paymentKey);
    return payment ? json(200, payment) : error(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.");
  }

  /**
   * 취소. `cancelAmount`가 없으면 남은 금액 전부다. 토스처럼 부분취소는 `PARTIAL_CANCELED`이고, 남은 금액을 모두 부분취소해도
   * 상태가 `CANCELED`로 바뀌지 않는다(R9). 전액 취소(금액 없음)만 `CANCELED`다.
   */
  async function cancel(request, paymentKey, { cancelReason, cancelAmount }) {
    if (!authorized(request)) {
      return unauthorized();
    }
    const payment = payments.get(paymentKey);
    if (payment) {
      countOf(payment.orderId).cancelRequests += 1;
    }
    return once(request, "cancel", async () => {
      if (!payment) {
        return error(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.");
      }
      const { cancelError } = settings.get(payment.orderId) ?? {};
      if (cancelError) {
        return error(cancelError.status, cancelError.code, cancelError.message);
      }
      if (payment.status === "CANCELED") {
        return error(400, "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다.");
      }
      if (payment.status !== "DONE" && payment.status !== "PARTIAL_CANCELED") {
        return error(403, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다.");
      }
      const amount = cancelAmount ?? payment.balanceAmount;
      if (amount <= 0 || amount > payment.balanceAmount) {
        return error(403, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다.");
      }
      const key = transactionKey();
      payment.status = cancelAmount === undefined && payment.status === "DONE" ? "CANCELED" : "PARTIAL_CANCELED";
      payment.cancels = [
        ...(payment.cancels ?? []),
        {
          transactionKey: key,
          cancelReason,
          cancelAmount: amount,
          canceledAt: kst(new Date()),
          cancelStatus: "DONE",
        },
      ];
      payment.balanceAmount -= amount;
      payment.lastTransactionKey = key;
      return json(200, payment);
    });
  }

  /** 같은 멱등 키의 요청은 한 번만 처리한다. 처리 중에 다시 오면 409, 끝난 뒤에는 같은 응답이다(토스와 같다). */
  async function once(request, kind, handle) {
    const header = request.headers["idempotency-key"];
    if (!header) {
      return handle();
    }
    const key = `${kind}:${header}`;
    if (idempotent.has(key)) {
      return idempotent.get(key) ?? error(409, "IDEMPOTENT_REQUEST_PROCESSING", "이전 멱등 요청이 처리 중입니다.");
    }
    idempotent.set(key, null);
    const store = idempotent;
    const response = await handle();
    store.set(key, response);
    return response;
  }

  function configure(orderId, { confirmDelayMs, confirmError, cancelError }) {
    const delayValid = confirmDelayMs === undefined || (Number.isInteger(confirmDelayMs) && confirmDelayMs >= 0);
    const errorValid = (value) =>
      value === undefined ||
      (Number.isInteger(value.status) && typeof value.code === "string" && typeof value.message === "string");
    if (!delayValid || !errorValid(confirmError) || !errorValid(cancelError)) {
      return error(
        400,
        "INVALID_SETTINGS",
        "confirmDelayMs는 0 이상 정수, confirmError·cancelError는 { status, code, message }다.",
      );
    }
    settings.set(orderId, { confirmDelayMs, confirmError, cancelError });
    return { status: 204 };
  }

  function inspect(orderId) {
    const { confirmRequests, cancelRequests } = counts.get(orderId) ?? { confirmRequests: 0, cancelRequests: 0 };
    const found = [...payments.values()].filter((payment) => payment.orderId === orderId);
    return json(200, { confirmRequests, cancelRequests, payments: found });
  }

  function reset() {
    payments = new Map();
    settings = new Map();
    counts = new Map();
    idempotent = new Map();
    return { status: 204 };
  }

  return createServer(async (request, response) => {
    const url = new URL(request.url, "http://fake-pg");
    let result = error(404, "NOT_FOUND", "없는 경로입니다.");
    try {
      for (const [method, pattern, handle] of routes) {
        const match = request.method === method && url.pathname.match(pattern);
        if (match) {
          result = await handle(request, url, match.slice(1).map(decodeURIComponent));
          break;
        }
      }
    } catch (cause) {
      console.error("[fake-pg] 요청 처리 실패", request.method, url.pathname, cause);
      result = error(400, "INVALID_REQUEST", "요청을 읽지 못했습니다.");
    }
    response.writeHead(result.status, { ...(result.type && { "Content-Type": result.type }), ...result.headers });
    response.end(result.body);
  });
}

function checkoutPage(query) {
  const fields = ["orderId", "orderName", "amount", "successUrl", "failUrl"];
  const hidden = fields
    .map((name) => `<input type="hidden" name="${name}" value="${escape(query.get(name) ?? "")}">`)
    .join("");
  const amount = Number(query.get("amount")).toLocaleString("ko-KR");
  const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>가짜 PG 결제 인증</title></head>
<body style="font-family: sans-serif; max-width: 28rem; margin: 3rem auto; padding: 0 1rem">
<h1>가짜 PG 결제 인증</h1>
<p>E2E용 가짜 결제입니다. 실제 결제가 일어나지 않습니다.</p>
<dl><dt>주문</dt><dd>${escape(query.get("orderName") ?? "")}</dd><dt>금액</dt><dd>${amount}원</dd></dl>
<form method="post" action="/checkout/approve">${hidden}<button type="submit">결제 승인</button></form>
<form method="post" action="/checkout/cancel">${hidden}<button type="submit">결제 취소</button></form>
</body></html>`;
  return { status: 200, type: "text/html; charset=utf-8", body: html };
}

function authorized(request) {
  return /^Basic \S+$/.test(request.headers.authorization ?? "");
}

function unauthorized() {
  return error(401, "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.");
}

function json(status, value) {
  return { status, type: "application/json", body: JSON.stringify(value) };
}

function error(status, code, message) {
  return json(status, { code, message });
}

function redirect(location) {
  return { status: 303, headers: { Location: location.toString() } };
}

async function text(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function body(request) {
  const raw = await text(request);
  return raw ? JSON.parse(raw) : {};
}

async function form(request) {
  return new URLSearchParams(await text(request));
}

/** 토스처럼 한국 시간 오프셋을 붙인 ISO 8601(예: 2026-10-21T15:04:05+09:00). */
function kst(date) {
  return `${new Date(date.getTime() + 9 * 3_600_000).toISOString().slice(0, 19)}+09:00`;
}

function transactionKey() {
  return `txrd_fake_${randomUUID().replaceAll("-", "")}`;
}

function escape(value) {
  const entities = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  return value.replace(/[&<>"']/g, (char) => entities[char]);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT ?? 8090);
  createFakePg().listen(port, () => console.log(`[fake-pg] http://0.0.0.0:${port}`));
}
