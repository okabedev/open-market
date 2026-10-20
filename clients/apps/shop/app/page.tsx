import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Label,
  loginErrorMessage,
} from "@openmarket/ui";
import Link from "next/link";
import { ProductCard } from "@/components/product-card";
import { bff } from "@/lib/bff";
import { loadCategories } from "@/lib/catalog";
import { currentSession } from "@/lib/session";

export default async function HomePage({ searchParams }: PageProps<"/">) {
  const { login_error: loginError } = await searchParams;
  const error = loginErrorMessage(typeof loginError === "string" ? loginError : undefined);
  const session = await currentSession();
  const search = await bff.publicClient("search");
  // 검색에 닿지 못해도 첫 화면은 그린다(새 상품 칸만 빠진다).
  const [latest, categories] = await Promise.all([
    search
      .GET("/v1/products", { params: { query: { sort: "LATEST", size: 8 } } })
      .then((result) => (result.response.ok ? result.data : undefined))
      .catch(() => undefined),
    loadCategories(),
  ]);
  return (
    <div className="grid gap-6">
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      <section className="grid gap-2 py-6">
        <h1 className="text-3xl font-bold tracking-tight">여러 판매자의 상품을 한곳에서</h1>
        <p className="text-muted-foreground">
          상품을 찾아보세요. 장바구니와 주문은 다음 마일스톤에서 열립니다.
        </p>
        <form action="/products" className="mt-2 flex gap-2" role="search">
          <Label htmlFor="home-q" className="sr-only">
            검색어
          </Label>
          <Input
            id="home-q"
            name="q"
            type="search"
            maxLength={100}
            placeholder="상품 이름, 상호로 찾기"
          />
          <Button type="submit">검색</Button>
        </form>
        {categories.length > 0 ? (
          <nav aria-label="카테고리" className="flex flex-wrap gap-2 pt-2">
            {categories.map((category) => (
              <Button key={category.categoryId} asChild variant="outline" size="sm">
                <Link href={`/products?categoryId=${category.categoryId}`}>{category.name}</Link>
              </Button>
            ))}
          </nav>
        ) : null}
      </section>
      {(latest?.items ?? []).length > 0 ? (
        <section className="grid gap-3" aria-label="새로 들어온 상품">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">새로 들어온 상품</h2>
            <Link href="/products?sort=LATEST" className="text-sm text-primary hover:underline">
              더 보기
            </Link>
          </div>
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {(latest?.items ?? []).map((item) => (
              <li key={item.productId}>
                <ProductCard {...item} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {session ? (
        <Card>
          <CardHeader>
            <CardTitle>내 정보</CardTitle>
            <CardDescription>
              프로필과 배송지를 관리하고, 판매자로 입점을 신청할 수 있습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button asChild>
              <a href="/my">내 정보</a>
            </Button>
            <Button asChild variant="outline">
              <a href="/my/seller">입점 신청</a>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>처음 오셨나요?</CardTitle>
            <CardDescription>
              이메일로 가입하고 인증 메일의 링크를 누르면 바로 시작할 수 있습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button asChild>
              <a href="/signup">가입하기</a>
            </Button>
            <Button asChild variant="outline">
              <a href="/auth/login">로그인</a>
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
