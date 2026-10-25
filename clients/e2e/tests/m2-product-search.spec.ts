import { expect, test } from "@playwright/test";
import { env } from "../support/env";
import { approvedSeller } from "../support/flows";
import { publish, registerProduct, searchIds, stop } from "../support/products";

/**
 * M2 완료 기준(docs/roadmap.md): 판매자가 이미지와 SKU로 상품을 등록하면 몇 초 안에 구매자 검색에 나온다. 판매를 중지하면 검색에서
 * 빠진다(CAT-1, CAT-2, SRC-1, SRC-3). 검색 반영은 outbox → Debezium → Kafka → search → Elasticsearch를 거친다.
 */
test("M2: 판매자가 등록한 상품이 몇 초 안에 구매자 검색에 나오고, 판매를 중지하면 빠진다", async ({
  browser,
}) => {
  test.setTimeout(240_000);
  const { seller, tag } = await approvedSeller(browser);
  const name = `무선블루투스이어폰 ${tag}`;
  const buyer = await (await browser.newContext()).newPage();

  let productId = "";
  await test.step("판매자: 이미지와 옵션 조합(SKU)으로 등록. 등록 중에는 검색에 없다", async () => {
    productId = await registerProduct(seller, {
      name,
      option: { name: "색상", values: ["화이트", "블랙"] },
      price: 59_000,
      stock: 5,
    });
    await expect(seller.getByText("등록 중")).toBeVisible();
    expect(await searchIds(tag)).not.toContain(productId);
  });

  await test.step("판매 시작 → 구매자 검색에 몇 초 안에 나온다(nori: 붙여 쓴 이름의 부분어로 찾는다)", async () => {
    await publish(seller);
    const started = Date.now();
    await expect
      .poll(() => searchIds(`이어폰 ${tag}`), { timeout: 15_000, intervals: [500] })
      .toContain(productId);
    test.info().annotations.push({
      type: "검색 반영",
      description: `${((Date.now() - started) / 1000).toFixed(1)}초`,
    });

    await buyer.goto(env.shop);
    // 자동완성 목록(datalist)이 붙은 검색창은 combobox다(M5).
    await buyer.getByRole("combobox", { name: "검색어" }).fill(`이어폰 ${tag}`);
    await buyer.getByRole("button", { name: "검색" }).click();
    await buyer.getByRole("link", { name: new RegExp(name) }).click();
    await expect(buyer.getByRole("heading", { name })).toBeVisible();
    await expect(buyer.getByRole("img", { name })).toBeVisible();
    await expect(buyer.getByRole("row", { name: /^화이트/ })).toContainText("구매 가능");
    await expect(buyer.getByRole("row", { name: /^블랙/ })).toContainText("59,000원");
  });

  await test.step("판매 중지 → 검색에서 빠지고, 상세는 판매 중지로 보인다", async () => {
    await stop(seller);
    await expect
      .poll(() => searchIds(tag), { timeout: 15_000, intervals: [500] })
      .not.toContain(productId);
    await buyer.goto(`${env.shop}/products?q=${tag}`);
    await expect(buyer.getByText("조건에 맞는 상품이 없습니다.")).toBeVisible();
    await buyer.goto(`${env.shop}/products/${productId}`);
    await expect(buyer.getByText("판매가 중지된 상품입니다.")).toBeVisible();
  });
});
