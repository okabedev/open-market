#!/usr/bin/env node
// `next start` 대신 앱을 띄운다. 사용법: 앱 디렉터리에서 `openmarket-serve --port 3000`.
//
// 앞단 프록시 없이 브라우저가 Next.js에 바로 붙는다. Next.js는 `X-Forwarded-*`가 이미 있으면 그대로 두므로, 브라우저가 넣은
// 값이 BFF가 gateway에 넘기는 클라이언트 주소가 된다(익명 레이트 리밋 우회). 요청을 넘기기 전에 이 헤더를 지우고 연결 주소를 넣는다.
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { join } from "node:path";
import { parseArgs } from "node:util";

/**
 * 브라우저가 보낸 `X-Forwarded-*`를 지우고 `X-Forwarded-For`를 연결 주소로 바꾼다. 나머지(host, proto, port)는 Next.js가
 * 요청에서 다시 채운다.
 * @param {import("node:http").IncomingMessage} request
 */
export function useConnectionAddress(request) {
  for (const name of Object.keys(request.headers)) {
    if (name.startsWith("x-forwarded-")) {
      delete request.headers[name];
    }
  }
  const address = request.socket.remoteAddress;
  if (address) {
    request.headers["x-forwarded-for"] = address;
  }
}

async function main() {
  const { values } = parseArgs({ options: { port: { type: "string" } } });
  const port = Number(values.port ?? process.env.PORT ?? 3000);
  const dir = process.cwd();
  // 앱이 빌드에 쓴 next를 그대로 쓴다.
  const nextModule = createRequire(join(dir, "package.json"))("next");
  const next = nextModule.default ?? nextModule;
  const app = next({ dev: false, dir, port });
  const handle = app.getRequestHandler();
  await app.prepare();
  createServer((request, response) => {
    useConnectionAddress(request);
    void handle(request, response);
  }).listen(port, () => {
    console.log(`> ready on http://localhost:${port}`);
  });
}

if (import.meta.main) {
  await main();
}
