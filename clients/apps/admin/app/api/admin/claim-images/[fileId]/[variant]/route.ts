import { bff } from "@/lib/bff";

/**
 * 중재 화면의 클레임 사진(BFF). 세션의 관리자 토큰으로 file의 관리자 경로(`/v1/admin/files/...`)를 불러 이미지만 넘긴다
 * (docs/architecture/file.md "비공개 파일"). 클레임에 붙은 파일만 받는다.
 */
export async function GET(
  _: Request,
  { params }: RouteContext<"/api/admin/claim-images/[fileId]/[variant]">,
): Promise<Response> {
  const { fileId, variant } = await params;
  return bff.privateFile("admin", fileId, variant);
}
