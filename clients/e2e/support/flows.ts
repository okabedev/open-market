import { expect, type Page } from "@playwright/test";
import { env } from "./env";
import { verificationLink } from "./mailpit";

/** shop에서 가입하고, 인증 메일 링크를 눌러 인증한 뒤 로그인한다. 내 정보 화면에서 끝난다. */
export async function signUpVerifyAndLogin(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  await page.goto(`${env.shop}/signup`);
  await page.getByLabel("이름").fill("홍길동");
  await page.getByLabel("이메일").fill(email);
  await page.getByLabel("비밀번호").fill(password);
  await page.getByRole("button", { name: "가입하기" }).click();
  await expect(page.getByText("인증 메일을 보냈습니다")).toBeVisible();

  await page.goto(await verificationLink(email));
  await expect(page.getByText("이메일을 인증했습니다")).toBeVisible();
  await page.getByRole("main").getByRole("link", { name: "로그인" }).click();
  await loginAtAuth(page, email, password);
  await page.waitForURL(`${env.shop}/my`);
  // 회원은 가입 이벤트로 비동기로 만들어진다(member.md "회원 생성").
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole("heading", { name: "내 정보" })).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
}

/** auth 로그인 화면에서 로그인한다. */
export async function loginAtAuth(page: Page, email: string, password: string): Promise<void> {
  await page.locator("input[name=username]").fill(email);
  await page.locator("input[name=password]").fill(password);
  await page.locator("button[type=submit]").click();
}

/** 관리자 앱에 로그인한다. 입점 심사 화면에서 끝난다. */
export async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto(env.admin);
  await page.getByRole("main").getByRole("link", { name: "로그인" }).click();
  await loginAtAuth(page, env.adminEmail, env.adminPassword);
  await page.waitForURL(`${env.admin}/seller-applications`);
}
