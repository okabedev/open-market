import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { loginAsAdmin, signUpVerifyAndLogin } from "../support/flows";
import { OAuthClient, rolesOf } from "../support/oauth";

/**
 * M1 완료 기준(docs/roadmap.md): 가입 → 인증 메일(Mailpit) → 로그인 → 입점 신청 → 관리자 승인 → 토큰 갱신 후 판매자 앱 진입.
 * 승인 전에는 판매자 API가 403이다.
 */
test("M1: 가입부터 판매자 승인과 판매자 센터 진입까지", async ({ browser }) => {
  const tag = uniqueTag();
  const email = `e2e-seller-${tag}@example.com`;
  const password = "e2e-seller-1234";
  const store = `이투이상회${tag.slice(-5)}`;

  const user = await (await browser.newContext()).newPage();
  await test.step("가입, 인증 메일, 로그인", async () => {
    await signUpVerifyAndLogin(user, email, password);
  });

  await test.step("입점 신청", async () => {
    await user.goto(`${env.shop}/my/seller`);
    await user.getByLabel("상호").fill(store);
    await user.getByLabel("사업자 등록 번호").fill("123-45-67891");
    await user.getByLabel("대표자").fill("홍길동");
    await user.getByLabel("은행").fill("국민은행");
    await user.getByLabel("예금주").fill("홍길동");
    await user.getByLabel("계좌 번호").fill("110-123-456789");
    await user.getByRole("button", { name: "입점 신청" }).click();
    await expect(user.getByText("심사 중인 신청이 있습니다")).toBeVisible();
  });

  const sellerApi = await OAuthClient.create("seller", env.sellerClientSecret, env.seller);
  let tokens = await sellerApi.login(email, password);
  const sellerMe = (accessToken: string) =>
    fetch(`${env.gateway}/api/member/v1/seller/me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

  await test.step("승인 전: 판매자 API 403, 판매자 센터는 권한 없음", async () => {
    expect(rolesOf(tokens)).not.toContain("SELLER");
    expect((await sellerMe(tokens.access_token)).status).toBe(403);
    await user.goto(env.seller);
    await user.getByRole("main").getByRole("link", { name: "로그인" }).click();
    await expect(user.getByText("판매자 권한이 없습니다")).toBeVisible();
  });

  await test.step("관리자 승인", async () => {
    const admin = await (await browser.newContext()).newPage();
    await loginAsAdmin(admin);
    const row = admin.getByRole("row", { name: new RegExp(store) });
    await expect(row).toBeVisible();
    // 관리자 목록은 개인정보를 가린다(NFR-7).
    await expect(row).toContainText("홍*동");
    await expect(row).toContainText("********6789");
    await row.getByRole("button", { name: "승인" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    await expect(admin.getByText("승인했습니다")).toBeVisible();
  });

  await test.step("토큰 갱신 후: 판매자 API 200", async () => {
    // 승인 이벤트가 outbox → Debezium → Kafka → auth로 가서 역할이 붙을 때까지 갱신을 되풀이한다.
    await expect
      .poll(
        async () => {
          tokens = await sellerApi.refresh(tokens);
          return rolesOf(tokens);
        },
        { message: "갱신한 토큰에 SELLER가 있어야 한다", timeout: 60_000, intervals: [2_000] },
      )
      .toContain("SELLER");
    const response = await sellerMe(tokens.access_token);
    expect(response.status).toBe(200);
    expect(((await response.json()) as { storeName: string }).storeName).toBe(store);
    await sellerApi.dispose();
  });

  await test.step("판매자 센터 진입", async () => {
    await user.getByRole("link", { name: "권한 다시 확인" }).click();
    await expect(user.getByRole("heading", { name: store })).toBeVisible();
    await expect(user.getByText("활동 중")).toBeVisible();
  });
});
