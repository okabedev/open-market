"use server";

import { problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { bff } from "@/lib/bff";

/** 탈퇴(ACC-4). 성공하면 로그아웃 화면으로 보내 BFF 세션과 auth 로그인 세션을 함께 끝낸다. */
export async function withdraw(): Promise<FormState> {
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST("/v1/members/me/withdraw");
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect("/withdrawn");
}
