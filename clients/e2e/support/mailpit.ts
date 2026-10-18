import { expect } from "@playwright/test";
import { env } from "./env";

type Message = { ID: string };

/** 그 주소로 온 인증 메일의 링크. notification이 보내기까지 기다린다. */
export async function verificationLink(email: string): Promise<string> {
  let link: string | undefined;
  await expect
    .poll(
      async () => {
        const search = await fetch(
          `${env.mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
        );
        const { messages } = (await search.json()) as { messages: Message[] };
        const first = messages[0];
        if (!first) {
          return undefined;
        }
        const message = (await (
          await fetch(`${env.mailpit}/api/v1/message/${first.ID}`)
        ).json()) as { Text: string };
        link = message.Text.match(/https?:\/\/\S+\/verify-email\?token=[\w-]+/)?.[0];
        return link;
      },
      { message: `${email}로 인증 메일이 와야 한다`, timeout: 60_000, intervals: [1_000, 2_000] },
    )
    .toBeTruthy();
  return link as string;
}
