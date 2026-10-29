import { bff } from "@/lib/bff";
import { currentSession } from "@/lib/session";
import { isUploadPurpose, PHOTO_MAX_BYTES } from "@/lib/upload";

/**
 * 구매자 사진 업로드(BFF). 클레임 사진은 `?purpose=CLAIM_IMAGE`, 리뷰 사진은 `?purpose=REVIEW_IMAGE`다. 브라우저는 gateway를 부르지
 * 않고 이 Route Handler로 올린다(clients/CLAUDE.md). 사용자 토큰을 붙여 file 서비스(`POST /v1/files?purpose=`)로 넘기고 `fileId`만
 * 돌려준다. 형식·크기 검사의 원본은 file이다(docs/architecture/file.md "검사"). 올린 사람은 붙기 전에도 미리 볼 수 있다(클레임 사진은
 * "비공개 파일", 리뷰 사진은 공개 경로).
 */
export async function POST(request: Request): Promise<Response> {
  if (!(await bff.isSameOrigin(request))) {
    return Response.json({ detail: "허용하지 않는 요청입니다." }, { status: 403 });
  }
  if (!(await currentSession())) {
    return Response.json({ detail: "로그인이 필요합니다." }, { status: 401 });
  }
  const purpose = new URL(request.url).searchParams.get("purpose");
  if (!isUploadPurpose(purpose)) {
    return Response.json({ detail: "올릴 수 없는 파일입니다." }, { status: 400 });
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
  if (file.size > PHOTO_MAX_BYTES) {
    return Response.json({ detail: "이미지는 10MB까지 올릴 수 있습니다." }, { status: 400 });
  }
  const files = await bff.serviceClient("file");
  const { data, error, response } = await files.POST("/v1/files", {
    params: { query: { purpose } },
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
