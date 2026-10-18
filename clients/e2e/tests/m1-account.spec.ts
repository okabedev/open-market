import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { signUpVerifyAndLogin } from "../support/flows";

/** M1 회원 기능(ACC-3, ACC-4): 프로필, 배송지, 탈퇴. 완료 기준 시나리오를 보조한다. */
test("M1: 프로필·배송지를 관리하고 탈퇴하면 로그아웃된다", async ({ page }) => {
  const tag = uniqueTag();
  const email = `e2e-account-${tag}@example.com`;
  await signUpVerifyAndLogin(page, email, "e2e-account-1234");

  await test.step("가입 폼은 잘못된 입력을 필드 옆에 알린다", async () => {
    const other = await page.context().browser()!.newPage();
    await other.goto(`${env.shop}/signup`);
    await other.getByLabel("이메일").fill("nope");
    await other.getByRole("button", { name: "가입하기" }).click();
    await expect(other.getByLabel("이메일")).toHaveAccessibleDescription("이메일 형식이 아닙니다.");
    await other.close();
  });

  await test.step("프로필 저장", async () => {
    await page.getByLabel("연락처").fill("010-1234-5678");
    await page.getByRole("button", { name: "저장" }).click();
    await expect(page.getByText("저장했습니다.")).toBeVisible();
  });

  await test.step("배송지: 추가, 기본 변경, 삭제", async () => {
    for (const [label, asDefault] of [
      ["집", false],
      ["회사", true],
    ] as const) {
      await page.goto(`${env.shop}/my/addresses/new`);
      await page.getByLabel("이름표").fill(label);
      await page.getByLabel("받는 사람", { exact: true }).fill("홍길동");
      await page.getByLabel("받는 사람 연락처").fill("010-1234-5678");
      await page.getByLabel("우편번호").fill("06236");
      await page.getByLabel("기본 주소").fill("서울특별시 강남구 테헤란로 123");
      if (asDefault) {
        await page.getByLabel("기본 배송지로 지정").check();
      }
      await page.getByRole("button", { name: "추가" }).click();
      await page.waitForURL(`${env.shop}/my/addresses`);
    }
    const items = page.getByRole("listitem");
    await expect(items).toHaveCount(2);
    await expect(items.first()).toContainText("회사");
    await expect(items.first()).toContainText("기본");

    await page.getByRole("button", { name: "기본으로" }).click();
    await expect(items.first()).toContainText("집");

    await items.first().getByRole("button", { name: "삭제" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "지우기" }).click();
    await expect(items).toHaveCount(1);
    await expect(items.first()).toContainText("회사");
    await expect(items.first()).toContainText("기본");
  });

  await test.step("탈퇴하면 로그아웃되어 처음 화면으로 간다", async () => {
    await page.goto(`${env.shop}/my/withdraw`);
    await page.getByRole("button", { name: "탈퇴하기" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "탈퇴" }).click();
    await page.waitForURL(`${env.shop}/`);
    await expect(page.getByRole("banner").getByRole("link", { name: "로그인" })).toBeVisible();
  });
});
