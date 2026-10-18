type CookieOptions = {
  maxAge: number;
  path: string;
  secure: boolean;
};

/** HttpOnly, SameSite=Lax 쿠키(docs/architecture/auth.md "보안 기본값"). */
export function serializeCookie(name: string, value: string, options: CookieOptions): string {
  const parts = [
    `${name}=${encodeURIComponent(value)}`,
    `Path=${options.path}`,
    `Max-Age=${options.maxAge}`,
    "HttpOnly",
    "SameSite=Lax",
  ];
  if (options.secure) {
    parts.push("Secure");
  }
  return parts.join("; ");
}

export function readCookie(request: Request, name: string): string | undefined {
  const header = request.headers.get("cookie");
  if (!header) {
    return undefined;
  }
  for (const part of header.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return undefined;
}

/** 앱마다 이름을 나눈다. 쿠키는 포트를 구분하지 않아 localhost:3000과 3001이 같은 이름을 공유하기 때문이다. */
export function sessionCookieName(app: string): string {
  return `om_sid_${app}`;
}

export function loginCookieName(app: string): string {
  return `om_login_${app}`;
}
