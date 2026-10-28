import { expect, type Page, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { loginAsAdmin } from "../support/flows";
import { produceUnreadableEvent } from "../support/kafka";

/** DLT 메시지 화면에서 그 이벤트의 행. 화면을 다시 읽어 `count`개가 될 때까지 기다린다(수집기가 비동기로 모은다). */
async function eventRows(admin: Page, query: string, eventId: string, count: number) {
  const rows = admin.getByRole("row").filter({ hasText: eventId });
  await expect(async () => {
    await admin.goto(`${env.admin}/dead-letters?topic=review.review${query}`);
    await expect(rows).toHaveCount(count, { timeout: 1_000 });
  }).toPass({ timeout: 60_000, intervals: [2_000] });
  return rows;
}

/**
 * M6 DLT 재처리(ADM-5, R14, ADR 0022): 페이로드를 읽을 수 없는 리뷰 작성 이벤트가 구독 그룹(file, notification)마다 DLT 메시지가 된다. 관리자가 file 행을
 * 다시 보내면 file만 다시 처리해(또 실패해 새 행) notification은 넘기고, 전체 탭에서 같은 이벤트의 행을 함께 본다. notification 행은
 * 사유를 남겨 버린다. 두 작업은 감사 로그에 남는다.
 */
test("M6: DLT 메시지를 그룹별로 보고, 다시 보내면 실패한 그룹만 처리하며, 사유를 남겨 버린다", async ({
  browser,
}) => {
  test.setTimeout(240_000);
  const admin = await (await browser.newContext()).newPage();
  await loginAsAdmin(admin);
  const eventId = produceUnreadableEvent(
    "review.review",
    "ReviewCreated",
    `e2e-dlt-${uniqueTag()}`,
  );

  await test.step("구독 그룹 file과 notification에 처리 대기 메시지가 하나씩 생긴다", async () => {
    const rows = await eventRows(admin, "", eventId, 2);
    await expect(rows.filter({ hasText: "notification" })).toHaveCount(1);
    await expect(rows.filter({ hasText: "file" }).first()).toContainText("review.review");
    await expect(admin.getByRole("link", { name: "DLT 메시지" })).toBeVisible();
  });

  await test.step("file 행을 다시 보내면 file만 또 실패해 새 행이 생기고, notification은 넘긴다", async () => {
    const fileRow = admin
      .getByRole("row")
      .filter({ hasText: eventId })
      .filter({ hasNotText: "notification" });
    await fileRow.getByRole("button", { name: "다시 보내기" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "다시 보내기" }).click();
    await expect(admin.getByText(/다시 보냈습니다/)).toBeVisible();

    // 처리 대기: 새 file 행 + notification 행
    await eventRows(admin, "", eventId, 2);
    // 전체: 다시 보낸 file 행 + 새 file 행 + notification 행. notification은 새 행이 없다.
    const all = await eventRows(admin, "&status=ALL", eventId, 3);
    await expect(all.filter({ hasText: "notification" })).toHaveCount(1);
    await expect(all.filter({ hasText: "다시 보냄" })).toHaveCount(1);
    await expect(all.first().getByText("같은 이벤트 3건")).toBeVisible();
  });

  await test.step("notification 행을 사유와 함께 버리면 버림 탭에 사유가 보인다", async () => {
    await admin.goto(`${env.admin}/dead-letters?topic=review.review`);
    const row = admin
      .getByRole("row")
      .filter({ hasText: eventId })
      .filter({ hasText: "notification" });
    await row.getByText("버리기", { exact: true }).first().click();
    await row.getByLabel("버리는 사유").fill("E2E 시험 메시지라 처리하지 않음");
    await row.getByRole("button", { name: "버리기" }).click();
    await expect(admin.getByText(/버렸습니다/)).toBeVisible();

    const discarded = await eventRows(admin, "&status=DISCARDED", eventId, 1);
    await expect(discarded).toContainText("E2E 시험 메시지라 처리하지 않음");
  });

  await test.step("다시 보내기와 버리기가 감사 로그에 남는다", async () => {
    await admin.goto(`${env.admin}/audit-logs?action=DEAD_LETTER_RETRIED&service=file`);
    await expect(admin.getByRole("row").nth(1)).toContainText("DLT 메시지 다시 보내기");
    await admin.goto(`${env.admin}/audit-logs?action=DEAD_LETTER_DISCARDED&service=notification`);
    await expect(admin.getByRole("row").nth(1)).toContainText("E2E 시험 메시지라 처리하지 않음");
  });
});
