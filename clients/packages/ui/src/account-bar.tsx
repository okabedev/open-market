import { Button } from "./button";

type AccountBarProps = {
  /** 로그인했으면 역할 목록, 아니면 null */
  roles: string[] | null;
  /** 로그인 화면으로 갈 때 돌아올 경로. 기본은 지금 앱의 첫 화면 */
  returnTo?: string;
};

const ROLE_NAMES: Record<string, string> = { BUYER: "구매자", SELLER: "판매자", ADMIN: "관리자" };

/**
 * 로그인 상태와 로그인·로그아웃 버튼. 서버 컴포넌트에서 쓴다.
 * 로그인은 BFF의 `/auth/login`(GET), 로그아웃은 `/auth/logout`(POST, 같은 Origin 폼)으로 간다.
 */
export function AccountBar({ roles, returnTo }: AccountBarProps) {
  if (!roles) {
    const href = returnTo ? `/auth/login?returnTo=${encodeURIComponent(returnTo)}` : "/auth/login";
    return (
      <Button asChild size="sm">
        <a href={href}>로그인</a>
      </Button>
    );
  }
  return (
    <form action="/auth/logout" method="post" className="flex items-center gap-3">
      <span className="text-sm text-muted-foreground">
        {roles.map((role) => ROLE_NAMES[role] ?? role).join(" · ") || "역할 없음"}
      </span>
      <Button type="submit" variant="outline" size="sm">
        로그아웃
      </Button>
    </form>
  );
}

const LOGIN_ERRORS: Record<string, string> = {
  access_denied: "이 앱을 쓸 권한이 없습니다.",
  invalid_grant: "로그인이 만료됐습니다. 다시 로그인해 주세요.",
};

/** 로그인 콜백이 돌려준 오류 코드(`?login_error=`)를 문구로 바꾼다. */
export function loginErrorMessage(code: string | undefined): string | null {
  if (!code) {
    return null;
  }
  return LOGIN_ERRORS[code] ?? "로그인하지 못했습니다. 다시 시도해 주세요.";
}
