"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { adminClient } from "@/lib/admin-client";
import { adminService } from "@/lib/admin-services";
import { reasonSchema, reviewResult } from "@/lib/review";

const uuid = z.uuid();

/** 폼의 서비스와 행 ID. 행은 메시지를 모은 서비스에만 있다. */
function target(form: FormData) {
  const service = adminService(form.get("service"));
  const id = uuid.safeParse(form.get("id"));
  return service && id.success ? { service, id: id.data } : null;
}

/**
 * DLT 메시지를 원래 토픽·파티션으로 다시 보낸다(ADM-5, docs/architecture/messaging.md "DLT 재처리"). 실패한 그룹만 처리하고, 또 실패하면
 * 새 행으로 들어온다. 이미 처리한 행(409)이나 전송 실패(503)는 서비스 문구를 보인다. 감사 로그에 남는다.
 */
export async function retryDeadLetter(_: FormState, form: FormData): Promise<FormState> {
  const found = target(form);
  if (!found) {
    return { status: "error", message: "메시지를 찾을 수 없습니다." };
  }
  const client = await adminClient(found.service);
  const { error, response } = await client.POST("/v1/admin/dead-letters/{id}/retry", {
    params: { path: { id: found.id } },
  });
  return reviewResult(error, response, form, "/dead-letters", "dead-letter-retried");
}

/** DLT 메시지를 버린다. 사유(1~500자)가 있어야 하고 메시지는 보내지 않는다. 감사 로그에 사유가 남는다. */
export async function discardDeadLetter(_: FormState, form: FormData): Promise<FormState> {
  const found = target(form);
  if (!found) {
    return { status: "error", message: "메시지를 찾을 수 없습니다." };
  }
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const client = await adminClient(found.service);
  const { error, response } = await client.POST("/v1/admin/dead-letters/{id}/discard", {
    params: { path: { id: found.id } },
    body: { reason: parsed.data.reason },
  });
  return reviewResult(error, response, form, "/dead-letters", "dead-letter-discarded");
}
