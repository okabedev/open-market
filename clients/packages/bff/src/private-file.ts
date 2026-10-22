/**
 * 비공개 파일(클레임 사진) 제공(docs/architecture/file.md "비공개 파일"). 브라우저에는 토큰이 없으므로 앱의 Route Handler가 세션의
 * 토큰으로 file을 불러 이미지만 넘긴다. 이미지 응답이라 OpenAPI 클라이언트 대신 fetch를 쓴다.
 */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const VARIANTS = new Set(["w300", "w800"]);

/** 사용자 경로(올린 사람, 클레임의 판매자)와 관리자 경로. */
export type PrivateFileScope = "user" | "admin";

/** file 서비스 경로. 화면이 넘긴 값이 파일 ID와 변형 이름이 아니면 null이다. */
export function privateFilePath(
  scope: PrivateFileScope,
  fileId: string,
  variant: string,
): string | null {
  if (!UUID.test(fileId) || !VARIANTS.has(variant)) {
    return null;
  }
  return `${scope === "admin" ? "/v1/admin/files" : "/v1/files"}/${fileId}/${variant}`;
}

export type PrivateFileOptions = {
  accessToken: string | null;
  gatewayUrl?: string;
  forwardedFor?: string;
  fetch?: (url: string, init: RequestInit) => Promise<Response>;
};

const NOT_FOUND = () => new Response(null, { status: 404 });

/**
 * file의 `path`를 사용자 토큰으로 불러 이미지와 캐시 헤더(`private`)만 넘긴다. 로그인하지 않았거나, 볼 수 없거나, 실패했으면 모두
 * 404다. 파일이 있는지 알리지 않는다.
 */
export async function fetchPrivateFile(
  path: string,
  options: PrivateFileOptions,
): Promise<Response> {
  if (!options.accessToken) {
    return NOT_FOUND();
  }
  const gatewayUrl = (
    options.gatewayUrl ??
    process.env.GATEWAY_URL ??
    "http://localhost:8080"
  ).replace(/\/+$/, "");
  const headers: Record<string, string> = { Authorization: `Bearer ${options.accessToken}` };
  if (options.forwardedFor) {
    headers["X-Forwarded-For"] = options.forwardedFor;
  }
  let upstream: Response;
  try {
    upstream = await (options.fetch ?? fetch)(`${gatewayUrl}/api/file${path}`, {
      headers,
      cache: "no-store",
    });
  } catch {
    return NOT_FOUND();
  }
  const contentType = upstream.headers.get("Content-Type") ?? "";
  if (!upstream.ok || !contentType.startsWith("image/")) {
    return NOT_FOUND();
  }
  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": contentType,
      "Cache-Control": upstream.headers.get("Cache-Control") ?? "private, no-store",
    },
  });
}
