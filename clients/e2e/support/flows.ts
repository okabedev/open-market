import { expect, type Browser, type Page } from "@playwright/test";
import { env, uniqueTag } from "./env";
import { verificationLink } from "./mailpit";
import { OAuthClient, rolesOf } from "./oauth";

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

/** 승인된 판매자. 판매자 센터 첫 화면에 들어와 있다. */
export type ApprovedSeller = {
  /** 판매자 센터에 로그인한 페이지 */
  seller: Page;
  /** 관리자 앱에 로그인한 페이지 */
  admin: Page;
  store: string;
  email: string;
  /** 실행마다 다른 값. 상품 이름에 넣어 검색으로 찾는다 */
  tag: string;
};

/**
 * 가입 → 인증 → 입점 신청 → 관리자 승인 → 역할이 붙은 토큰 → 판매자 센터 진입(M1 완료 기준과 같은 흐름). M2부터 상품 시나리오가 앞
 * 단계로 쓴다.
 */
export async function approvedSeller(browser: Browser): Promise<ApprovedSeller> {
  const tag = uniqueTag();
  const email = `e2e-seller-${tag}@example.com`;
  const password = "e2e-seller-1234";
  const store = `이투이상회${tag.slice(-5)}`;

  const seller = await (await browser.newContext()).newPage();
  await signUpVerifyAndLogin(seller, email, password);
  await seller.goto(`${env.shop}/my/seller`);
  await seller.getByLabel("상호").fill(store);
  await seller.getByLabel("사업자 등록 번호").fill("123-45-67891");
  await seller.getByLabel("대표자").fill("홍길동");
  await seller.getByLabel("은행").fill("국민은행");
  await seller.getByLabel("예금주").fill("홍길동");
  await seller.getByLabel("계좌 번호").fill("110-123-456789");
  await seller.getByRole("button", { name: "입점 신청" }).click();
  await expect(seller.getByText("심사 중인 신청이 있습니다")).toBeVisible();

  const admin = await (await browser.newContext()).newPage();
  await loginAsAdmin(admin);
  const row = admin.getByRole("row", { name: new RegExp(store) });
  await row.getByRole("button", { name: "승인" }).click();
  await admin.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
  await expect(admin.getByText("승인했습니다")).toBeVisible();

  // 승인 이벤트가 auth와 catalog에 닿을 때까지 기다린다. 역할이 붙은 토큰이 나오면 판매자 센터에 새로 로그인한다.
  const api = await OAuthClient.create("seller", env.sellerClientSecret, env.seller);
  let tokens = await api.login(email, password);
  await expect
    .poll(async () => rolesOf((tokens = await api.refresh(tokens))), {
      message: "갱신한 토큰에 SELLER가 있어야 한다",
      timeout: 60_000,
      intervals: [2_000],
    })
    .toContain("SELLER");
  await api.dispose();
  await seller.goto(env.seller);
  await seller.getByRole("main").getByRole("link", { name: "로그인" }).click();
  await expect(seller.getByRole("heading", { name: store })).toBeVisible();
  return { seller, admin, store, email, tag };
}
