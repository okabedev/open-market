import { expect, test } from "@playwright/test";
import { env } from "../support/env";
import { approvedSeller } from "../support/flows";

/**
 * M6 감사 로그(ADM-4, NFR-8, ADR 0023): 관리자가 member(입점 승인)와 settlement(정산 작업 즉시 실행)에서 한 작업이 감사 로그 화면에
 * 한 목록으로 최근 순으로 보이고, 서비스·작업한 사람으로 거를 수 있다.
 */
test("M6: 여러 서비스의 관리자 작업이 감사 로그 한 목록에 최근 순으로 보이고 서비스·작업한 사람으로 걸러진다", async ({
  browser,
}) => {
  test.setTimeout(180_000);
  const { admin } = await approvedSeller(browser);

  await admin.goto(`${env.admin}/settlements`);
  await admin.getByRole("button", { name: "정산 작업 지금 실행" }).click();
  await admin.getByRole("alertdialog").getByRole("button", { name: "지금 실행" }).click();
  await expect(admin.getByText(/정산 작업을 돌렸습니다/)).toBeVisible();

  const rows = admin.getByRole("row");
  let actorId = "";
  await test.step("전체: 정산 작업 즉시 실행(settlement)이 입점 승인(member)보다 위에 있다", async () => {
    await admin.getByRole("link", { name: "감사 로그" }).click();
    await expect(admin.getByRole("heading", { name: "감사 로그" })).toBeVisible();
    await expect(admin.getByText("응답하지 않은 서비스")).toHaveCount(0);
    const run = rows.filter({ hasText: "정산 작업 즉시 실행" }).first();
    const approval = rows.filter({ hasText: "입점 승인" }).first();
    await expect(run).toContainText("settlement");
    await expect(approval).toContainText("member");
    await expect(rows.nth(1)).toContainText("정산 작업 즉시 실행");
    actorId = (await run.getByRole("cell").nth(4).innerText()).trim();
    expect(actorId).not.toBe("");
  });

  await test.step("서비스 member로 거르면 member 기록만 보인다", async () => {
    await admin.getByLabel("서비스").selectOption("member");
    await admin.getByRole("button", { name: "조회" }).click();
    await expect(admin).toHaveURL(/service=member/);
    await expect(rows.nth(1)).toContainText("입점 승인");
    await expect(rows.filter({ hasText: "settlement" })).toHaveCount(0);
  });

  await test.step("작업한 사람으로 거른다", async () => {
    await admin.goto(`${env.admin}/audit-logs?actorId=${actorId}&action=SETTLEMENT_RUN_EXECUTED`);
    await expect(rows.nth(1)).toContainText("정산 작업 즉시 실행");
    await admin.goto(
      `${env.admin}/audit-logs?actorId=0199f000-0000-7000-8000-00000000dead&action=SETTLEMENT_RUN_EXECUTED`,
    );
    await expect(admin.getByText("감사 로그가 없습니다.")).toBeVisible();
  });
});
