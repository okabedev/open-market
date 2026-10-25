import { bff } from "@/lib/bff";
import { suggestionKeyword } from "@/lib/suggestions";

/**
 * 검색창 자동완성(BFF, SRC-2). 브라우저가 입력을 멈추고 잠깐 뒤 부른다. search `GET /v1/suggestions`의 상품명만 돌려준다. 같은 검색어는
 * 브라우저가 60초 동안 다시 묻지 않는다(판매 중 상품이 바뀌어도 자동완성이 잠깐 늦는 것은 괜찮다). search에 닿지 못하면 빈 목록이다.
 */
export async function GET(request: Request): Promise<Response> {
  const q = suggestionKeyword(new URL(request.url).searchParams.get("q"));
  if (!q) {
    return empty();
  }
  const search = await bff.publicClient("search");
  const result = await search
    .GET("/v1/suggestions", { params: { query: { q } } })
    .catch(() => null);
  if (!result?.response.ok || !result.data) {
    return empty();
  }
  const items = (result.data.items ?? []).flatMap((item) => (item.name ? [item.name] : []));
  return Response.json({ items }, { headers: { "Cache-Control": "public, max-age=60" } });
}

function empty(): Response {
  return Response.json({ items: [] }, { headers: { "Cache-Control": "no-store" } });
}
