import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import {
  accept,
  paidOrder,
  requestClaim,
  sellerOrderIds,
  ship,
  waitForSellerOrder,
} from "../support/fulfillment";
import { addAddress } from "../support/orders";
import { publish, registerProduct, stockOf } from "../support/products";

/**
 * M4(docs/roadmap.md) 판매자 화면: 판매자가 결제된 하위 주문을 발주 확인하고 송장을 넣어 발송하면 배송 시뮬레이터가 배송 완료로 바꾼다
 * (FUL-1, FUL-2). 반품은 회수품을 보고 재입고를 골라 승인하면 환불(반품 배송비 차감)되고 재입고된다. 교환은 거부하면 사유가 남고,
 * 다시 신청한 것을 승인하면 교환 재발송을 발주 확인·발송한다(CLM-2, CLM-3). 구매자 클레임은 shop 화면으로 신청한다.
 *
 * run.sh가 자동 구매확정을 배송 완료 30초 뒤로 줄이므로 클레임은 배송 완료를 본 즉시 신청한다.
 */
test("M4: 판매자가 발주 확인·발송하고, 반품을 승인하면 환불·재입고되고, 거부한 교환을 다시 받아 재발송한다", async ({
  browser,
}) => {
  test.setTimeout(420_000);
  const { seller, tag } = await approvedSeller(browser);
  const name = `워싱데님셔츠 ${tag}`;
  let productId = "";
  await test.step("판매자: 옵션 없는 상품(19,900원, 재고 5)을 등록하고 판매를 시작한다", async () => {
    productId = await registerProduct(seller, { name, price: 19_900, stock: 5 });
    await publish(seller);
  });

  const buyer = await (await browser.newContext()).newPage();
  const email = `e2e-buyer-${uniqueTag()}@example.com`;
  const password = "e2e-buyer-1234";
  const orders: string[] = [];
  await test.step("구매자: 가입하고 1개씩 두 번 주문해 결제한다", async () => {
    await signUpVerifyAndLogin(buyer, email, password);
    await addAddress(buyer);
    orders.push(await paidOrder(buyer, productId, 1));
    orders.push(await paidOrder(buyer, productId, 1));
  });
  const [returningOrder = "", exchangingOrder = ""] = orders;

  // 최근 결제 순이라 나중에 결제한 주문이 앞에 온다. 먼저 결제한 것을 반품, 나중 것을 교환에 쓴다.
  let returning = "";
  let exchanging = "";
  await test.step("판매자: 발주 확인 대기 탭에 두 하위 주문이 결제 완료로 보인다", async () => {
    await expect(async () => {
      [exchanging = "", returning = ""] = await sellerOrderIds(seller, name);
      expect(returning).not.toBe("");
    }).toPass({ timeout: 30_000 });
    await seller.goto(`${env.seller}/orders`);
    await seller.getByRole("link", { name: "발주 확인 대기" }).click();
    await expect(seller.getByText("전체 2건")).toBeVisible();
    await expect(seller.getByRole("row", { name: new RegExp(name) }).first()).toContainText(
      "결제 완료",
    );
  });

  await test.step("발주를 확인하면 상품 준비 중이 되고 발송 대기 탭으로 옮겨 간다", async () => {
    await accept(seller, returning);
    await expect(seller.getByText("상품 준비 중").first()).toBeVisible();
    // 목록의 진행 상태는 order가 배송 이벤트로 바꾼다.
    await expect(async () => {
      await seller.goto(`${env.seller}/orders?status=to-ship`);
      await expect(seller.getByText("전체 1건")).toBeVisible({ timeout: 1_000 });
    }).toPass({ timeout: 30_000 });
  });

  await test.step("송장 번호가 숫자 10~14자리가 아니면 발송하지 않고, 맞으면 하이픈을 빼고 발송한다", async () => {
    await seller.goto(`${env.seller}/orders/${returning}`);
    await seller.getByLabel("택배사").selectOption({ label: "CJ대한통운" });
    await seller.getByLabel("송장 번호").fill("12345");
    await seller.getByRole("button", { name: "발송", exact: true }).click();
    await expect(seller.getByText("송장 번호는 숫자 10~14자리입니다.")).toBeVisible();

    await ship(seller, returning, "1234-5678-9012");
    await expect(seller.getByRole("cell", { name: "CJ대한통운 123456789012" })).toBeVisible();
  });

  await test.step("다른 하위 주문도 발주 확인하고 발송한다", async () => {
    await accept(seller, exchanging);
    await ship(seller, exchanging, "2234567890");
  });

  await test.step("배송 시뮬레이터가 배송 완료로 바꾸면 구매자가 곧바로 반품(단순 변심, 사진)과 교환(상품 불량)을 신청한다", async () => {
    await waitForSellerOrder(seller, returning, (page) =>
      page.getByRole("cell", { name: "배송 완료", exact: true }),
    );
    await requestClaim(buyer, returningOrder, {
      type: "반품",
      reason: "단순 변심",
      description: "사이즈가 맞지 않아요",
      photo: true,
    });
    await waitForSellerOrder(seller, exchanging, (page) =>
      page.getByRole("cell", { name: "배송 완료", exact: true }),
    );
    await requestClaim(buyer, exchangingOrder, {
      type: "교환",
      reason: "상품 불량",
      description: "단추가 떨어져 왔어요",
    });
  });

  await test.step("판매자: 클레임 진행 중 탭에 반품 신청과 교환 신청이 보인다", async () => {
    await expect(async () => {
      await seller.goto(`${env.seller}/orders?status=claims`);
      await expect(seller.getByText("전체 2건")).toBeVisible({ timeout: 1_000 });
    }).toPass({ timeout: 30_000 });
    await expect(seller.getByText("반품 신청")).toBeVisible();
    await expect(seller.getByText("교환 신청")).toBeVisible();
  });

  await test.step("반품 상세에 사유·귀책·설명과 사진이 보인다. 사진은 로그인하지 않으면 받을 수 없다", async () => {
    await seller.goto(`${env.seller}/orders/${returning}`);
    await expect(seller.getByText("단순 변심 (귀책 구매자)")).toBeVisible();
    await expect(seller.getByText("사이즈가 맞지 않아요")).toBeVisible();
    const image = seller.getByRole("img", { name: "클레임 사진 1" });
    await expect(image).toBeVisible();
    await expect
      .poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth))
      .toBeGreaterThan(0);

    const src = (await image.getAttribute("src")) ?? "";
    const anonymous = await browser.newContext();
    const response = await anonymous.request.get(`${env.seller}${src}`);
    expect(response.status()).toBe(404);
    await anonymous.close();
  });

  let onHandBefore = 0;
  await test.step("재입고를 고르지 않으면 승인하지 않고, 재입고를 골라 승인하면 반품 배송비를 뺀 금액이 환불되고 재입고된다", async () => {
    onHandBefore = (await stockOf(seller, productId)).onHand;
    await seller.goto(`${env.seller}/orders/${returning}`);
    const approve = async () => {
      await seller.getByRole("button", { name: "승인", exact: true }).click();
      await seller.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    };
    await approve();
    await expect(seller.getByText("재입고할지 골라 주세요.")).toBeVisible();
    await seller.getByLabel("재입고한다(다시 팔 수 있다)").check();
    await approve();
    await expect(seller.getByText("클레임을 승인했습니다.")).toBeVisible();
    await expect(seller.getByText("13,900원")).toBeVisible();

    await waitForSellerOrder(seller, returning, (page) => page.getByText("반품 완료"));
    await expect(seller.getByText("반품 완료").first()).toBeVisible();
    await expect
      .poll(async () => (await stockOf(seller, productId)).onHand, { timeout: 30_000 })
      .toBe(onHandBefore + 1);
  });

  await test.step("교환을 거부하면 사유가 남는다", async () => {
    await seller.goto(`${env.seller}/orders/${exchanging}`);
    await expect(seller.getByText("상품 불량 (귀책 판매자)")).toBeVisible();
    await seller.getByLabel("거부 사유").fill("검수 결과 단추가 붙어 있었습니다");
    await seller.getByRole("button", { name: "거부", exact: true }).click();
    await expect(seller.getByText("클레임을 거부했습니다.")).toBeVisible();
    await expect(seller.getByText("교환 거부")).toBeVisible();
    await expect(seller.getByText("검수 결과 단추가 붙어 있었습니다")).toBeVisible();
  });

  await test.step("구매자가 다시 신청한 교환을 승인하면 교환 재발송을 발주 확인하고 발송한다", async () => {
    await requestClaim(buyer, exchangingOrder, {
      type: "교환",
      reason: "상품 불량",
      description: "다시 봐 주세요. 단추가 떨어져 있어요",
    });
    await waitForSellerOrder(seller, exchanging, (page) => page.getByText("교환 신청"));
    await seller.getByRole("button", { name: "승인", exact: true }).click();
    await seller.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    await expect(seller.getByText("클레임을 승인했습니다.")).toBeVisible();
    await expect(seller.getByText("발주 확인 (교환 재발송)")).toBeVisible();

    await accept(seller, exchanging);
    await ship(seller, exchanging, "3234567890");
    await expect(seller.getByRole("row", { name: /교환 재발송/ })).toContainText(
      "CJ대한통운 3234567890",
    );
  });
});
