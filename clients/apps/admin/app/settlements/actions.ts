"use server";

import { problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";

/**
 * 확정된 정산을 지급 완료로 기록한다(STL-3, ADM-3). 실제 송금은 하지 않는다. 이미 지급 완료면 그대로 성공이다. settlement가
 * `SettlementPaidOut`을 내고(판매자 알림) 감사 로그(`SETTLEMENT_PAID_OUT`)를 남긴다. 그 정산 상세로 돌아간다.
 */
export async function payOut(_: FormState, form: FormData): Promise<FormState> {
  const settlementId = z.uuid().safeParse(form.get("settlementId"));
  if (!settlementId.success) {
    return { status: "error", message: "정산을 찾을 수 없습니다." };
  }
  const settlement = await bff.serviceClient("settlement");
  const { error, response } = await settlement.POST("/v1/admin/settlements/{settlementId}/payout", {
    params: { path: { settlementId: settlementId.data } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(`/settlements/${settlementId.data}?notice=paid-out`);
}

/**
 * 정산 작업을 바로 돌린다(STL-2). 끝난 기간 중 확정하지 않은 판매자·기간을 확정한다. 정기 작업과 겹치거나 다시 돌려도 결과가 같다
 * (settlement.md "정산 확정"). 확정한 건수를 정산 목록에 보인다.
 */
export async function runSettlement(): Promise<FormState> {
  const settlement = await bff.serviceClient("settlement");
  const { data, error, response } = await settlement.POST("/v1/admin/settlement-runs");
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(`/settlements?notice=settlement-run&confirmed=${data?.confirmedCount ?? 0}`);
}
