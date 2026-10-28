import { expect, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/**
 * 시드 관리자의 2단계 인증 비밀 값과 마지막으로 쓴 단계. 시나리오 파일끼리 나눠 쓰려고 파일에 둔다. Playwright가 실행마다 test-results를 비우고
 * run.sh가 실행마다 빈 스택을 띄우므로, 처음 로그인에서 등록하고 그 실행 안에서만 쓴다.
 */
const STATE = path.resolve(import.meta.dirname, "../test-results/.admin-totp.json");

type State = { secret: string; lastStep: number };

function base32Decode(text: string): Buffer {
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const c of text.replace(/[\s=]/g, "").toUpperCase()) {
    buffer = (buffer << 5) | ALPHABET.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** 그 단계의 6자리 코드(RFC 6238, HMAC-SHA1, 30초). auth와 같은 계산이다. */
export function totpCode(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac("sha1", base32Decode(secret)).update(counter).digest();
  const offset = hash[hash.length - 1]! & 0x0f;
  const binary = hash.readUInt32BE(offset) & 0x7fffffff;
  return String(binary % 1_000_000).padStart(6, "0");
}

function currentStep(): number {
  return Math.floor(Date.now() / 30_000);
}

/** 아직 쓰지 않은 단계. auth는 앞뒤 한 단계까지 받고 쓴 단계는 다시 받지 않으므로, 다음 단계까지 썼으면 그 단계가 될 때까지 기다린다. */
async function unusedStep(lastStep: number): Promise<number> {
  while (lastStep >= currentStep() + 1) {
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  return Math.max(currentStep(), lastStep + 1);
}

function save(state: State): void {
  mkdirSync(path.dirname(STATE), { recursive: true });
  writeFileSync(STATE, JSON.stringify(state));
}

/**
 * 비밀번호 다음의 2단계 인증 화면을 지난다(docs/architecture/auth.md "관리자 2단계 인증"). 처음이면 등록 화면의 비밀 키로 등록하고 복구 코드
 * 화면에서 계속을 누른다. 등록했으면 저장한 비밀 값으로 코드를 만든다.
 */
export async function passSecondFactor(page: Page): Promise<void> {
  const enroll = page.getByRole("heading", { name: "2단계 인증 등록" });
  const verify = page.getByRole("heading", { name: "2단계 인증", exact: true });
  await expect(enroll.or(verify)).toBeVisible();
  if (await enroll.isVisible()) {
    const secret = (await page.locator("#totp-secret").innerText()).trim();
    const step = currentStep();
    await page.getByLabel("인증 앱의 6자리 코드").fill(totpCode(secret, step));
    await page.getByRole("button", { name: "등록" }).click();
    await expect(page.getByRole("heading", { name: "복구 코드" })).toBeVisible();
    save({ secret, lastStep: step });
    await page.getByRole("link", { name: "계속" }).click();
    return;
  }
  if (!existsSync(STATE)) {
    throw new Error(
      "관리자가 2단계 인증을 등록했는데 비밀 값이 없다. 빈 스택에서 다시 돌린다(infra/e2e/run.sh)",
    );
  }
  const state = JSON.parse(readFileSync(STATE, "utf8")) as State;
  const step = await unusedStep(state.lastStep);
  await page.getByLabel("인증 코드 또는 복구 코드").fill(totpCode(state.secret, step));
  await page.getByRole("button", { name: "확인" }).click();
  save({ ...state, lastStep: step });
}
