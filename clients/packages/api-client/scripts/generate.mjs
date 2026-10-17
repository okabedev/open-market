// openapi/<서비스>.json에서 src/generated/<서비스>.ts 타입을 만든다.
// openapi/*.json은 백엔드 테스트(common-test의 OpenApiSnapshot)가 쓴다. 손으로 고치지 않는다.
//   node scripts/generate.mjs          타입을 다시 쓴다
//   node scripts/generate.mjs --check  다시 만든 결과가 커밋된 파일과 다르면 실패한다(CI)
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import openapiTS, { astToString } from "openapi-typescript";

const HEADER =
  "// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.\n" +
  "// 원본: openapi/";

const check = process.argv.includes("--check");
const root = new URL("../", import.meta.url);
const specDir = new URL("openapi/", root);
const outDir = new URL("src/generated/", root);

const specs = (await readdir(specDir)).filter((file) => file.endsWith(".json")).sort();
const stale = [];

await mkdir(outDir, { recursive: true });
for (const file of specs) {
  const service = file.slice(0, -".json".length);
  const ast = await openapiTS(new URL(file, specDir));
  const content = `${HEADER}${file}\n\n${astToString(ast)}`;
  const target = new URL(`${service}.ts`, outDir);

  if (!check) {
    await writeFile(target, content);
    console.log(`생성: src/generated/${service}.ts`);
    continue;
  }
  const current = await readFile(target, "utf8").catch(() => null);
  if (current !== content) {
    stale.push(`src/generated/${service}.ts`);
  }
}

if (stale.length > 0) {
  console.error(
    `OpenAPI 문서와 생성한 타입이 다르다: ${stale.join(", ")}\n` +
      "`pnpm --filter @openmarket/api-client generate`로 다시 만들어 커밋한다.",
  );
  process.exit(1);
}
if (check) {
  console.log(`생성한 타입이 최신이다(${specs.length}개)`);
}
