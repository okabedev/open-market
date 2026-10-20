import { expect, test } from "@playwright/test";
import { env } from "../support/env";
import { approvedSeller } from "../support/flows";
import { productIdOf, publish, registerProduct, searchIds } from "../support/products";

/**
 * M2 상품 관리: 재고 조정과 이력(CAT-3), 품절 상품은 검색 뒤로(SRC-3), 상품 수정(CAT-2), 관리자 제재(CAT-5, ADM-2).
 */
test("M2: 재고 조정으로 품절되면 검색 뒤로 가고, 관리자가 제재하면 구매자에게 보이지 않는다", async ({
  browser,
}) => {
  test.setTimeout(240_000);
  const { seller, admin, store, tag } = await approvedSeller(browser);
  const buyer = await (await browser.newContext()).newPage();

  // 품절될 상품을 나중에 판매 시작해 최신순으로 앞에 있게 한다. 품절되면 뒤로 가야 한다.
  const keeperName = `반팔티셔츠 ${tag} 재고있음`;
  const keeper = await registerProduct(seller, { name: keeperName, price: 19_900, stock: 10 });
  await publish(seller);
  const soldOutName = `반팔티셔츠 ${tag} 품절예정`;
  const soldOut = await registerProduct(seller, {
    name: soldOutName,
    option: { name: "사이즈", values: ["M", "L"] },
    price: 21_900,
    stock: 1,
  });
  await publish(seller);
  await expect
    .poll(() => searchIds(tag, "LATEST"), { timeout: 15_000, intervals: [500] })
    .toEqual([soldOut, keeper]);

  await test.step("재고 조정과 이력: 모든 SKU를 0으로 줄이면 품절이 되고 검색 뒤로 간다", async () => {
    for (const option of ["M", "L"]) {
      await seller.getByLabel(`${option} 증감 수량`).fill("-1");
      await seller.getByLabel(`${option} 조정 사유`).fill("파손");
      await seller
        .getByRole("row", { name: new RegExp(`^${option}`) })
        .getByRole("button", { name: "조정" })
        .click();
      await expect(seller.getByText("재고를 조정했습니다")).toBeVisible();
      await expect(seller.getByRole("row", { name: new RegExp(`^${option}`) })).toContainText(
        "품절",
      );
    }
    await seller.getByRole("row", { name: /^M/ }).getByRole("link", { name: "보기" }).click();
    await expect(seller.getByRole("row", { name: /파손/ })).toContainText("-1");
    await seller.getByRole("link", { name: "상품으로" }).click();

    await expect
      .poll(() => searchIds(tag, "LATEST"), { timeout: 15_000, intervals: [500] })
      .toEqual([keeper, soldOut]);
    await buyer.goto(`${env.shop}/products?q=${tag}&sort=LATEST`);
    const cards = buyer.getByRole("region", { name: "검색 결과" }).getByRole("listitem");
    await expect(cards.nth(0)).toContainText(keeperName);
    await expect(cards.nth(1)).toContainText("품절");
  });

  await test.step("수정: 가격을 바꾸면 판매자 상세와 구매자 상세에 반영된다", async () => {
    await seller.getByRole("link", { name: "수정" }).click();
    await seller.getByLabel("1번째 줄 가격").fill("23900");
    await seller.getByRole("button", { name: "저장" }).click();
    await expect(seller.getByText("상품을 고쳤습니다")).toBeVisible();
    expect(productIdOf(seller)).toBe(soldOut);
    await buyer.goto(`${env.shop}/products/${soldOut}`);
    await expect(buyer.getByRole("row", { name: /^M/ })).toContainText("23,900원");
    await expect(buyer.getByText("모든 옵션이 품절입니다.")).toBeVisible();
  });

  await test.step("관리자: 판매자 필터로 찾아 사유를 적고 제재한다", async () => {
    await admin.goto(`${env.admin}/sellers`);
    await admin
      .getByRole("row", { name: new RegExp(store) })
      .getByRole("link", { name: "상품 보기" })
      .click();
    await expect(admin.getByText(`판매자: ${store}`)).toBeVisible();
    const row = admin.getByRole("row", { name: new RegExp(keeperName) });
    await row.locator("summary", { hasText: "제재" }).click();
    await row.getByLabel("제재 사유").fill("허위 광고");
    await row.getByRole("button", { name: "제재" }).click();
    await expect(admin.getByText("상품을 제재했습니다")).toBeVisible();
    await admin.getByRole("link", { name: "판매 제한" }).click();
    await expect(admin.getByRole("row", { name: new RegExp(keeperName) })).toContainText(
      "허위 광고",
    );
  });

  await test.step("제재 뒤: 구매자 검색·상세에서 빠지고, 판매자는 사유를 보지만 고치거나 다시 열 수 없다", async () => {
    await expect
      .poll(() => searchIds(tag, "LATEST"), { timeout: 15_000, intervals: [500] })
      .toEqual([soldOut]);
    const detail = await buyer.goto(`${env.shop}/products/${keeper}`);
    expect(detail?.status()).toBe(404);

    await seller.goto(`${env.seller}/products/${keeper}`);
    await expect(seller.getByText("관리자가 판매를 제한했습니다. 사유: 허위 광고")).toBeVisible();
    await expect(seller.getByRole("link", { name: "수정" })).toHaveCount(0);
    await expect(seller.getByRole("button", { name: "판매 중지" })).toHaveCount(0);
  });
});
