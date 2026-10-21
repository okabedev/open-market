import { expect, test, type Page } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin, type ApprovedSeller } from "../support/flows";
import {
  addAddress,
  addToCart,
  configureFakePg,
  fakePgOrder,
  openCheckout,
  openFakePgCheckout,
  orderFromCart,
  placeOrder,
  removeFromCart,
  waitForOrderText,
} from "../support/orders";
import { publish, registerProduct, stockOf } from "../support/products";

/**
 * M3 완료 기준(docs/roadmap.md): 판매자 둘의 상품을 한 번에 주문하고 승인하면 주문이 `PAID`가 되고 하위 주문 둘이 생긴다. 재고 부족
 * 주문은 취소되고 예약이 풀린다. 결제하지 않은 주문은 만료된다. 만료 직후 승인된 결제는 자동 취소된다(ORD-1~4, PAY-1, PAY-3, CAT-4).
 * 결제는 가짜 PG(ADR 0018)가 받고, 결제 기한·만료·보상 취소 주기는 run.sh가 짧게 준다(결제 기한 `env.paymentTimeoutMs`).
 * 시나리오는 판매자 둘과 구매자 하나를 함께 쓰고 순서대로 돈다.
 */
test.describe.configure({ mode: "serial" });

let sellerA: ApprovedSeller;
let sellerB: ApprovedSeller;
let buyer: Page;
const products = { shirt: "", socks: "", cap: "" };
const names = { shirt: "", socks: "", cap: "" };

test.beforeAll(async ({ browser }) => {
  test.setTimeout(300_000);
  sellerA = await approvedSeller(browser);
  sellerB = await approvedSeller(browser);
  names.shirt = `오버핏맨투맨 ${sellerA.tag}`;
  names.socks = `면양말세트 ${sellerB.tag}`;
  names.cap = `한정판볼캡 ${sellerB.tag}`;
  products.shirt = await registerProduct(sellerA.seller, {
    name: names.shirt,
    price: 12_000,
    stock: 10,
  });
  await publish(sellerA.seller);
  products.socks = await registerProduct(sellerB.seller, {
    name: names.socks,
    price: 8_000,
    stock: 10,
  });
  await publish(sellerB.seller);
  // 한 개만 있는 상품. 두 주문이 동시에 가져가려 한다.
  products.cap = await registerProduct(sellerB.seller, { name: names.cap, price: 5_000, stock: 1 });
  await publish(sellerB.seller);

  buyer = await (await browser.newContext()).newPage();
  await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
  await addAddress(buyer);
});

test("판매자 둘의 상품을 한 번에 주문하고 결제하면 PAID가 되고, 판매자마다 자기 하위 주문만 본다", async () => {
  test.setTimeout(180_000);
  await addToCart(buyer, products.shirt, 1);
  await addToCart(buyer, products.socks, 2);
  const orderId = await orderFromCart(buyer);

  await openFakePgCheckout(buyer);
  await expect(buyer.getByText("28,000원")).toBeVisible();
  await buyer.getByRole("button", { name: "결제 승인" }).click();
  await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(buyer.getByRole("heading", { name: sellerA.store })).toBeVisible();
  await expect(buyer.getByRole("heading", { name: sellerB.store })).toBeVisible();

  await buyer.goto(`${env.shop}/orders/${orderId}`);
  await expect(buyer.getByText("결제 완료")).toBeVisible();
  // 주문명은 첫 항목의 상품명 + "외 1건"이다.
  await expect(buyer.getByRole("heading", { name: / 외 1건$/ })).toBeVisible();

  // 하위 주문 둘. 판매자는 자기 항목과 하위 주문 금액만 본다.
  await sellerA.seller.goto(`${env.seller}/orders`);
  const rowA = sellerA.seller.getByRole("row", { name: new RegExp(names.shirt) });
  await expect(rowA).toContainText("12,000원");
  await expect(sellerA.seller.getByText(names.socks)).toHaveCount(0);
  await sellerB.seller.goto(`${env.seller}/orders`);
  const rowB = sellerB.seller.getByRole("row", { name: new RegExp(names.socks) });
  await expect(rowB).toContainText("16,000원");
  await expect(sellerB.seller.getByText(names.shirt)).toHaveCount(0);

  // 결제가 확정되면 예약이 실재고에서 빠진다.
  await expect
    .poll(() => stockOf(sellerB.seller, products.socks), { timeout: 30_000 })
    .toEqual({ onHand: 8, reserved: 0, available: 8 });
});

test("마지막 한 개를 두 주문이 동시에 가져가면 하나는 재고 부족으로 취소되고, 결제하지 않은 주문은 만료되어 예약이 풀린다", async () => {
  test.setTimeout(env.paymentTimeoutMs + 150_000);
  await addToCart(buyer, products.cap, 1);
  // 같은 장바구니로 주문서를 둘 연다. 둘 다 판매 가능 수량 1을 보고 주문 생성의 수량 확인을 지난다.
  const second = await buyer.context().newPage();
  await openCheckout(buyer);
  await openCheckout(second);
  const ids = await Promise.all([placeOrder(buyer), placeOrder(second)]);
  await second.close();

  // 재고 예약은 한 주문만 성공한다(catalog가 행을 잠그고 판매 가능 수량을 확인한다).
  let winner = "";
  await expect
    .poll(
      async () => {
        // 한 페이지로 차례로 연다(동시에 이동하면 앞 이동이 취소된다).
        const statuses: string[] = [];
        for (const id of ids) {
          statuses.push(await orderStatusText(buyer, id));
        }
        winner = ids[statuses.indexOf("결제 대기")] ?? "";
        return [...statuses].sort();
      },
      { timeout: 30_000, intervals: [1_000] },
    )
    .toEqual(["결제 대기", "취소됨"]);
  const loser = ids.find((id) => id !== winner) as string;
  await waitForOrderText(buyer, loser, "재고가 모자라 주문이 취소되었습니다.");
  expect(await stockOf(sellerB.seller, products.cap)).toEqual({
    onHand: 1,
    reserved: 1,
    available: 0,
  });

  // 결제하지 않은 주문은 결제 기한이 지나면 만료되고 예약이 풀린다.
  await waitForOrderText(
    buyer,
    winner,
    "결제 기한이 지나 주문이 취소되었습니다.",
    env.paymentTimeoutMs + 30_000,
  );
  await expect
    .poll(() => stockOf(sellerB.seller, products.cap), { timeout: 30_000 })
    .toEqual({ onHand: 1, reserved: 0, available: 1 });
  expect((await fakePgOrder(winner)).payments).toEqual([]);

  await removeFromCart(buyer, names.cap);
});

test("결제 기한 직전에 승인을 요청해 응답이 만료 뒤에 오면, 주문은 취소로 남고 승인된 결제는 자동으로 전액 취소된다", async () => {
  test.setTimeout(env.paymentTimeoutMs + 150_000);
  await addToCart(buyer, products.shirt, 1);
  await openCheckout(buyer);
  // 결제 기한은 주문 시각부터 센다. 주문 직전 시각을 잰다.
  const placedBefore = Date.now();
  const orderId = await placeOrder(buyer);
  await openFakePgCheckout(buyer);

  // 결제 기한 2초 전에 승인을 요청하고, 가짜 PG가 8초 뒤에 응답한다(gateway·payment 읽기 타임아웃 10초 안).
  // 응답을 기다리는 사이 만료 작업(2초 주기)이 주문을 취소하고, payment는 진행 중인 결제에 취소 요청을 남긴다.
  await configureFakePg(orderId, { confirmDelayMs: 8_000 });
  const approveAt = placedBefore + env.paymentTimeoutMs - 2_000;
  await buyer.waitForTimeout(Math.max(0, approveAt - Date.now()));
  await buyer.getByRole("button", { name: "결제 승인" }).click();

  // 토스는 승인했지만 그사이 취소 요청이 남아 payment가 409 PAYMENT_CANCELLED로 답한다(payment.md "보상 취소").
  // BFF는 결제 화면으로 돌려보내 승인된 금액이 자동으로 취소된다고 알린다.
  await expect(
    buyer.getByText("주문이 취소되어 결제할 수 없습니다. 승인된 금액은 자동으로 취소됩니다."),
  ).toBeVisible({ timeout: 30_000 });

  // 보상 취소 작업(2초 주기)이 가짜 PG에 전액 취소를 요청한다.
  await expect
    .poll(async () => (await fakePgOrder(orderId)).payments.map((payment) => payment.status), {
      timeout: 30_000,
      intervals: [1_000],
    })
    .toEqual(["CANCELED"]);
  const pg = await fakePgOrder(orderId);
  expect(pg.confirmRequests).toBe(1);
  expect(pg.cancelRequests).toBeGreaterThanOrEqual(1);

  // 주문은 결제 완료가 되지 않고, 판매자에게 보이지 않으며, 예약도 풀린다.
  await waitForOrderText(buyer, orderId, "취소됨");
  await sellerA.seller.goto(`${env.seller}/orders`);
  await expect(sellerA.seller.getByText("전체 1건")).toBeVisible();
  await expect
    .poll(() => stockOf(sellerA.seller, products.shirt), { timeout: 30_000 })
    .toEqual({ onHand: 9, reserved: 0, available: 9 });
});

/** 구매자 주문 상세의 상태 이름. */
async function orderStatusText(page: Page, orderId: string): Promise<string> {
  await page.goto(`${env.shop}/orders/${orderId}`);
  for (const label of ["재고 확인 중", "결제 대기", "결제 완료", "취소됨"]) {
    if (await page.getByText(label, { exact: true }).count()) {
      return label;
    }
  }
  return "";
}
