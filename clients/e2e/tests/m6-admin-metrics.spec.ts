import { expect, type Page, test } from "@playwright/test";
import { env } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import { paidOrder } from "../support/fulfillment";
import { addAddress } from "../support/orders";
import { publish, registerProduct } from "../support/products";

type TodayMetrics = { orders: number; paid: number; gmv: number; approved: number };

/** 운영 지표 표의 오늘(KST) 행. 열은 날짜, 주문, 결제된 주문, GMV, 승인, 거절, 미정, 실패율이다. */
async function todayMetrics(admin: Page): Promise<TodayMetrics> {
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
  await admin.goto(`${env.admin}/metrics`);
  const cells = admin.getByRole("row", { name: new RegExp(`^${today}`) }).getByRole("cell");
  await expect(cells).toHaveCount(8);
  const number = async (index: number) =>
    Number((await cells.nth(index).innerText()).replace(/[^0-9]/g, ""));
  return {
    orders: await number(1),
    paid: await number(2),
    gmv: await number(3),
    approved: await number(4),
  };
}

/**
 * M6 관리자 운영 지표(ADM-4): 구매자가 결제하면 관리자 운영 지표의 오늘 행에 주문, 결제된 주문, 거래액, 결제 승인이 늘어난다. 시나리오가
 * 같은 스택을 쓰므로 결제 전·후 차이로 본다. 자정(KST)을 넘기는 실행은 다루지 않는다.
 */
test("M6: 결제한 주문이 관리자 운영 지표의 오늘 주문 수·GMV·결제 승인에 더해진다", async ({
  browser,
}) => {
  test.setTimeout(240_000);
  const { seller, admin, tag } = await approvedSeller(browser);
  const productId = await registerProduct(seller, {
    name: `지표니트 ${tag}`,
    price: 27_000,
    stock: 5,
  });
  await publish(seller);

  const before = await todayMetrics(admin);
  await expect(admin.getByRole("link", { name: "운영 지표" })).toBeVisible();

  const buyer = await (await browser.newContext()).newPage();
  await signUpVerifyAndLogin(buyer, `e2e-buyer-${tag}@example.com`, "e2e-buyer-1234");
  await addAddress(buyer);
  await paidOrder(buyer, productId, 1);

  // 결제 승인은 이벤트로 order에 닿는다(PAID).
  await expect(async () => {
    expect(await todayMetrics(admin)).toEqual({
      orders: before.orders + 1,
      paid: before.paid + 1,
      gmv: before.gmv + 27_000,
      approved: before.approved + 1,
    });
  }).toPass({ timeout: 30_000, intervals: [1_000] });

  await test.step("31일을 넘는 기간은 화면이 먼저 막는다", async () => {
    await admin.goto(`${env.admin}/metrics?from=2026-01-01&to=2026-03-01`);
    await expect(admin.getByText("기간은 31일까지입니다.").first()).toBeVisible();
  });
});
