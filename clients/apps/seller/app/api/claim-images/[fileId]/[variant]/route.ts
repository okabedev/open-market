import { bff } from "@/lib/bff";

/**
 * 클레임 사진(BFF). 브라우저는 토큰이 없으므로 세션의 토큰으로 file의 비공개 경로를 불러 이미지만 넘긴다(docs/architecture/file.md
 * "비공개 파일"). 클레임의 판매자인지는 file이 확인하고, 볼 수 없으면 404다.
 */
export async function GET(
  _: Request,
  { params }: RouteContext<"/api/claim-images/[fileId]/[variant]">,
): Promise<Response> {
  const { fileId, variant } = await params;
  return bff.privateFile("user", fileId, variant);
}
