import { bff } from "@/lib/bff";
import { PRODUCT_LIMITS } from "@/lib/product-rules";
import { currentSession } from "@/lib/session";

/**
 * 상품 이미지 업로드(BFF). 브라우저는 gateway를 부르지 않고 이 Route Handler로 올린다(clients/CLAUDE.md). 사용자 토큰을 붙여 file
 * 서비스(`POST /v1/seller/files`)로 넘기고 `fileId`만 돌려준다. 형식·크기 검사의 원본은 file이다(docs/architecture/file.md "검사").
 */
export async function POST(request: Request): Promise<Response> {
  if (!(await currentSession())) {
    return Response.json({ detail: "로그인이 필요합니다." }, { status: 401 });
  }
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return Response.json({ detail: "올릴 파일을 골라 주세요." }, { status: 400 });
  }
  const file = form.get("file");
  if (!(file instanceof File) || file.size === 0) {
    return Response.json({ detail: "올릴 파일을 골라 주세요." }, { status: 400 });
  }
  if (file.size > PRODUCT_LIMITS.imageBytes) {
    return Response.json({ detail: "이미지는 10MB까지 올릴 수 있습니다." }, { status: 400 });
  }
  const files = await bff.serviceClient("file");
  const { data, error, response } = await files.POST("/v1/seller/files", {
    params: { query: { purpose: "PRODUCT_IMAGE" } },
    body: { file: "" },
    bodySerializer: () => {
      const body = new FormData();
      body.set("file", file);
      return body;
    },
  });
  if (!response.ok || !data?.fileId) {
    // file의 오류 응답(ProblemDetail)은 OpenAPI에 적혀 있지 않아 타입이 없다.
    const problem = error as { detail?: unknown } | undefined;
    const detail =
      typeof problem?.detail === "string" ? problem.detail : "이미지를 올리지 못했습니다.";
    return Response.json({ detail }, { status: response.ok ? 502 : response.status });
  }
  return Response.json({ fileId: data.fileId }, { status: 201 });
}
