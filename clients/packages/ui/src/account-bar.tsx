type AccountBarProps = {
  /** 로그인했으면 역할 목록, 아니면 null */
  roles: string[] | null;
  /** 로그인 콜백이 돌려준 오류 코드(`?login_error=`) */
  loginError?: string | undefined;
};

const LOGIN_ERRORS: Record<string, string> = {
  access_denied: "이 앱을 쓸 권한이 없습니다.",
  invalid_grant: "로그인이 만료됐습니다. 다시 로그인해 주세요.",
};

const ROLE_NAMES: Record<string, string> = { BUYER: "구매자", SELLER: "판매자", ADMIN: "관리자" };

/**
 * 로그인 상태와 로그인·로그아웃 버튼. 서버 컴포넌트에서 쓴다.
 * 로그인은 BFF의 `/auth/login`(GET), 로그아웃은 `/auth/logout`(POST, 같은 Origin 폼)으로 간다.
 */
export function AccountBar({ roles, loginError }: AccountBarProps) {
  const error = loginError
    ? (LOGIN_ERRORS[loginError] ?? "로그인하지 못했습니다. 다시 시도해 주세요.")
    : null;
  return (
    <nav aria-label="계정">
      {error ? <p role="alert">{error}</p> : null}
      {roles ? (
        <form action="/auth/logout" method="post">
          <span>
            로그인됨({roles.map((role) => ROLE_NAMES[role] ?? role).join(", ") || "역할 없음"})
          </span>{" "}
          <button type="submit">로그아웃</button>
        </form>
      ) : (
        <a href="/auth/login">로그인</a>
      )}
    </nav>
  );
}
