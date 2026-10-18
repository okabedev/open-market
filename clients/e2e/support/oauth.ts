import { request, type APIRequestContext } from "@playwright/test";
import { createHash, randomBytes } from "node:crypto";
import { env } from "./env";

export type Tokens = { access_token: string; refresh_token: string };

/**
 * 브라우저 없이 Authorization Code + PKCE로 토큰을 받는다. 화면 E2E와 별도로 gateway API의 권한(403/200)을 직접 확인할 때 쓴다.
 * 쿠키(auth 로그인 세션)는 이 요청 컨텍스트 안에만 있다.
 */
export class OAuthClient {
  private constructor(
    private readonly http: APIRequestContext,
    private readonly clientId: string,
    private readonly secret: string,
    private readonly redirectUri: string,
  ) {}

  static async create(clientId: string, secret: string, appUrl: string): Promise<OAuthClient> {
    return new OAuthClient(await request.newContext(), clientId, secret, `${appUrl}/auth/callback`);
  }

  async login(email: string, password: string): Promise<Tokens> {
    const verifier = randomBytes(48).toString("base64url");
    const challenge = createHash("sha256").update(verifier).digest("base64url");
    const authorize = `${env.auth}/oauth2/authorize?${new URLSearchParams({
      response_type: "code",
      client_id: this.clientId,
      scope: "openid profile api",
      redirect_uri: this.redirectUri,
      state: "e2e",
      code_challenge: challenge,
      code_challenge_method: "S256",
    })}`;
    const html = { Accept: "text/html" };
    await this.http.get(authorize, { headers: html });
    const loginPage = await (await this.http.get(`${env.auth}/login`)).text();
    const csrf = loginPage.match(/name="_csrf" value="([^"]+)"/)?.[1];
    if (!csrf) {
      throw new Error("로그인 화면에 CSRF 토큰이 없다");
    }
    await this.http.post(`${env.auth}/login`, {
      form: { username: email, password, _csrf: csrf },
      maxRedirects: 0,
    });
    const redirect = await this.http.get(authorize, { headers: html, maxRedirects: 0 });
    const code = new URL(redirect.headers().location ?? "", env.auth).searchParams.get("code");
    if (!code) {
      throw new Error(
        `인가 코드를 받지 못했다: ${redirect.status()} ${redirect.headers().location}`,
      );
    }
    return this.token({
      grant_type: "authorization_code",
      code,
      redirect_uri: this.redirectUri,
      code_verifier: verifier,
    });
  }

  /** 리프레시 토큰으로 갱신한다. 역할은 갱신할 때 DB에서 다시 읽는다(auth.md "토큰"). */
  async refresh(tokens: Tokens): Promise<Tokens> {
    return this.token({ grant_type: "refresh_token", refresh_token: tokens.refresh_token });
  }

  private async token(form: Record<string, string>): Promise<Tokens> {
    const response = await this.http.post(`${env.auth}/oauth2/token`, {
      form,
      headers: {
        Authorization: `Basic ${Buffer.from(`${this.clientId}:${this.secret}`).toString("base64")}`,
      },
    });
    if (!response.ok()) {
      throw new Error(`토큰 요청 실패: ${response.status()} ${await response.text()}`);
    }
    return (await response.json()) as Tokens;
  }

  async dispose(): Promise<void> {
    await this.http.dispose();
  }
}

/** 액세스 토큰의 roles 클레임. 서명은 확인하지 않는다(gateway가 한다). */
export function rolesOf(tokens: Tokens): string[] {
  const payload = tokens.access_token.split(".")[1] ?? "";
  return (
    (JSON.parse(Buffer.from(payload, "base64url").toString()) as { roles?: string[] }).roles ?? []
  );
}
