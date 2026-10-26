"use server";

import { type FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { percentToBp } from "@/lib/commission";
import { reviewResult } from "@/lib/review";

function categoryId(form: FormData): string | null {
  const parsed = z.uuid().safeParse(form.get("categoryId"));
  return parsed.success ? parsed.data : null;
}

const INVALID_TARGET: FormState = { status: "error", message: "카테고리를 찾을 수 없습니다." };

/**
 * 소분류 카테고리의 수수료율을 정한다(STL-1). 화면은 %로 받고 settlement에는 bp로 보낸다. 바꾼 요율은 그 뒤에 생기는 정산 대상부터 쓴다.
 * 같은 요율이면 바뀌지 않는다. settlement가 감사 로그(`COMMISSION_RATE_CHANGED`)를 남긴다.
 */
export async function setCommissionRate(_: FormState, form: FormData): Promise<FormState> {
  const id = categoryId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const values = { rate: String(form.get("rate") ?? "") };
  const rateBp = percentToBp(values.rate);
  if (rateBp === null) {
    return {
      status: "error",
      fieldErrors: { rate: "요율은 0~100%, 소수 둘째 자리까지입니다." },
      values,
    };
  }
  const settlement = await bff.serviceClient("settlement");
  const { error, response } = await settlement.PUT("/v1/admin/commission-rates/{categoryId}", {
    params: { path: { categoryId: id } },
    body: { rateBp },
  });
  return reviewResult(error, response, form, "/commission-rates", "commission-set");
}

/** 카테고리의 수수료율을 지워 기본 요율로 돌린다. 정한 요율이 없으면 그대로 성공이다. */
export async function resetCommissionRate(_: FormState, form: FormData): Promise<FormState> {
  const id = categoryId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const settlement = await bff.serviceClient("settlement");
  const { error, response } = await settlement.DELETE("/v1/admin/commission-rates/{categoryId}", {
    params: { path: { categoryId: id } },
  });
  return reviewResult(error, response, form, "/commission-rates", "commission-reset");
}
