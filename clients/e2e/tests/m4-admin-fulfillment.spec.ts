import { expect, test, type Page } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import {
  accept,
  buyNowPaid,
  openDelivery,
  requestClaim,
  sellerOrderIds,
  ship,
  waitForDelivery,
} from "../support/fulfillment";
import { addAddress, configureFakePg } from "../support/orders";
import { publish, registerProduct } from "../support/products";

/** 머리글 알림 링크를 눌러 알림함을 열고, 그 문구의 알림을 열어 이동한 화면에서 끝난다. */
async function openNotification(page: Page, message: RegExp): Promise<void> {
  await page.getByRole("link", { name: /^알림 \d+개 안 읽음$/ }).click();
  await expect(page.getByRole("heading", { name: "알림" })).toBeVisible();
  const item = page.getByRole("listitem", { name: message }).first();
  await expect(item).toContainText("새 알림");
  await item.getByRole("button", { name: /보기$/ }).click();
}

/**
 * M4(docs/roadmap.md) 관리자 화면과 세 앱의 인앱 알림(NTF-1): 판매자가 거부한 교환을 구매자가 중재 요청하면 관리자가 알림을 받고 사진을
 * 보고 판매자 귀책으로 승인한다(CLM-3, ADM-2). 토스(가짜 PG)가 반품 환불을 거절하면 관리자가 알림을 받고, 원인을 푼 뒤 환불을 다시
 * 시도한다(PAY-2). 판매자·구매자도 머리글 알림에서 새 클레임과 결과를 보고 해당 화면으로 간다.
 */
test("M4: 관리자가 알림을 받고 교환 중재를 승인하고, 거절된 환불을 다시 시도한다. 세 앱이 알림을 보인다", async ({
  browser,
}) => {
  test.setTimeout(420_000);
  const { seller, admin, tag } = await approvedSeller(browser);
  const name = `울니트가디건 ${tag}`;
  let productId = "";
  await test.step("판매자: 옵션 없는 상품(39,900원, 재고 5)을 판매한다", async () => {
    productId = await registerProduct(seller, { name, price: 39_900, stock: 5 });
    await publish(seller);
  });

  const buyer = await (await browser.newContext()).newPage();
  const orders: string[] = [];
  await test.step("구매자: 두 번 바로 구매하고, 판매자가 둘 다 발주 확인·발송한다", async () => {
    await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
    await addAddress(buyer);
    orders.push(await buyNowPaid(buyer, productId, 1));
    orders.push(await buyNowPaid(buyer, productId, 1));
    let ids: string[] = [];
    await expect(async () => {
      ids = await sellerOrderIds(seller, name);
      expect(ids).toHaveLength(2);
    }).toPass({ timeout: 30_000 });
    for (const [index, sellerOrderId] of ids.entries()) {
      await accept(seller, sellerOrderId);
      await ship(seller, sellerOrderId, `72345678${index}0`);
    }
  });
  const [refunding = "", mediating = ""] = orders;

  await test.step("배송 완료를 보면 구매자가 곧바로 반품(단순 변심)과 교환(오배송, 사진)을 신청한다", async () => {
    await waitForDelivery(buyer, refunding, (page) =>
      page.getByRole("link", { name: "반품·교환 신청" }),
    );
    await requestClaim(buyer, refunding, {
      type: "반품",
      reason: "단순 변심",
      description: "생각보다 두꺼워요",
    });
    await waitForDelivery(buyer, mediating, (page) =>
      page.getByRole("link", { name: "반품·교환 신청" }),
    );
    await requestClaim(buyer, mediating, {
      type: "교환",
      reason: "오배송",
      description: `주문과 다른 색상이 왔어요 ${tag}`,
      photo: true,
    });
  });

  await test.step("판매자: 머리글 알림에서 새 클레임을 열면 클레임 진행 중 목록으로 간다", async () => {
    await expect(async () => {
      await seller.goto(`${env.seller}/orders`);
      await expect(seller.getByRole("link", { name: /^알림 \d+개 안 읽음$/ })).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await expect(async () => {
      await seller.goto(`${env.seller}/notifications`);
      await expect(
        seller.getByText("교환 신청이 들어왔습니다. 승인하거나 거부해 주세요."),
      ).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await expect(seller.getByText(/새 주문이 들어왔습니다\. 울니트가디건/).first()).toBeVisible();
    await seller
      .getByRole("listitem", { name: /교환 신청이 들어왔습니다/ })
      .getByRole("button", { name: /보기$/ })
      .click();
    await expect(seller).toHaveURL(`${env.seller}/orders?status=claims`);
  });

  const [mediatingSo = "", refundingSo = ""] = await sellerOrderIds(seller, name);
  await test.step("토스가 환불을 거절하도록 하고 판매자가 반품을 승인한다. 교환은 거부한다", async () => {
    await configureFakePg(refunding, {
      cancelError: {
        status: 403,
        code: "NOT_CANCELABLE_PAYMENT",
        message: "취소 할 수 없는 결제 입니다.",
      },
    });
    await seller.goto(`${env.seller}/orders/${refundingSo}`);
    await seller.getByLabel("재입고한다(다시 팔 수 있다)").check();
    await seller.getByRole("button", { name: "승인", exact: true }).click();
    await seller.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    await expect(seller.getByText("클레임을 승인했습니다.")).toBeVisible();

    await seller.goto(`${env.seller}/orders/${mediatingSo}`);
    await seller.getByLabel("거부 사유").fill("주문하신 색상으로 보냈습니다");
    await seller.getByRole("button", { name: "거부", exact: true }).click();
    await expect(seller.getByText("클레임을 거부했습니다.")).toBeVisible();
  });

  await test.step("구매자: 거부 알림을 열면 주문으로 가고, 배송 조회에서 중재를 요청한다", async () => {
    await expect(async () => {
      await buyer.goto(`${env.shop}/notifications`);
      await expect(buyer.getByText(/교환 신청이 거부됐습니다/)).toBeVisible({ timeout: 1_000 });
    }).toPass({ timeout: 30_000 });
    await buyer
      .getByRole("listitem", { name: /교환 신청이 거부됐습니다/ })
      .getByRole("button", { name: /보기$/ })
      .click();
    await expect(buyer).toHaveURL(`${env.shop}/orders/${mediating}`);
    await openDelivery(buyer, mediating);
    await buyer.getByRole("button", { name: "중재 요청" }).click();
    await buyer.getByRole("alertdialog").getByRole("button", { name: "중재 요청" }).click();
    await expect(buyer.getByText("중재를 요청했습니다.")).toBeVisible();
  });

  await test.step("관리자: 중재 알림을 열고 사진을 본 뒤 판매자 귀책으로 교환을 승인한다", async () => {
    await expect(async () => {
      await admin.goto(`${env.admin}/notifications`);
      await expect(admin.getByText("교환 클레임에 중재 요청이 들어왔습니다.").first()).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await admin.goto(`${env.admin}/seller-applications`);
    await openNotification(admin, /교환 클레임에 중재 요청이 들어왔습니다/);
    await expect(admin).toHaveURL(`${env.admin}/claims`);

    const card = admin.locator("section").filter({ hasText: `주문과 다른 색상이 왔어요 ${tag}` });
    await expect(card.getByText("주문하신 색상으로 보냈습니다")).toBeVisible();
    const image = card.getByRole("img", { name: "클레임 사진 1" });
    await expect
      .poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth))
      .toBeGreaterThan(0);
    await expect(card.getByLabel(/^구매자/)).toHaveCount(0);
    await card.getByLabel(/^판매자/).check();
    await card.getByRole("button", { name: "승인", exact: true }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    await expect(admin.getByText(/중재로 승인했습니다/)).toBeVisible();
    await expect(admin.getByText(`주문과 다른 색상이 왔어요 ${tag}`)).toHaveCount(0);

    await seller.goto(`${env.seller}/orders/${mediatingSo}`);
    await expect(seller.getByText("발주 확인 (교환 재발송)")).toBeVisible();
  });

  await test.step("관리자: 환불 실패 알림과 목록을 보고, 원인을 푼 뒤 다시 시도하면 환불이 끝난다", async () => {
    await expect(async () => {
      await admin.goto(`${env.admin}/payment-failures`);
      await expect(admin.getByRole("cell", { name: "NOT_CANCELABLE_PAYMENT" })).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 60_000 });
    await admin.goto(`${env.admin}/notifications`);
    await expect(
      admin.getByText(
        "환불이 실패했습니다(NOT_CANCELABLE_PAYMENT). 환불 재시도 화면에서 확인해 주세요.",
      ),
    ).toBeVisible();

    await configureFakePg(refunding, {});
    await admin.goto(`${env.admin}/payment-failures`);
    const row = admin.getByRole("row").filter({ hasText: refunding });
    await expect(row).toContainText("33,900원");
    await row.getByRole("button", { name: "다시 시도" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "다시 시도" }).click();
    await expect(admin.getByText(/환불을 다시 시도합니다/)).toBeVisible();
    await expect(admin.getByRole("row").filter({ hasText: refunding })).toHaveCount(0);

    await waitForDelivery(buyer, refunding, (page) => page.getByText("반품 완료"));
  });

  await test.step("모두 읽음으로 하면 머리글에 안 읽은 수가 없어진다", async () => {
    await admin.goto(`${env.admin}/notifications`);
    await admin.getByRole("button", { name: "모두 읽음" }).click();
    await expect(admin.getByRole("link", { name: "알림", exact: true })).toBeVisible();
    await expect(admin.getByText("새 알림")).toHaveCount(0);
  });
});
