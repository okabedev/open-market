// 가입, 이메일 인증, Authorization Code + PKCE 로그인(clients/e2e/support/oauth.ts와 같은 흐름).
// 사용자가 많으므로 단계마다 http.batch로 한 묶음씩 보낸다. 로그인 세션 쿠키는 사용자마다 따로 둔다(CookieJar).
import http from "k6/http";
import crypto from "k6/crypto";
import encoding from "k6/encoding";
import { sleep } from "k6";
import { env, clientAddress } from "./env.js";

const BATCH = 50;
const PASSWORD = "load-test-1234";

export function uuid() {
  const b = new Uint8Array(crypto.randomBytes(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function chunks(items, size = BATCH) {
  const out = [];
  for (let i = 0; i < items.length; i += size) {
    out.push(items.slice(i, i + size));
  }
  return out;
}

function expectStatus(responses, expected, what) {
  responses.forEach((r) => {
    if (!expected.includes(r.status)) {
      throw new Error(`${what} 실패: ${r.status} ${r.url} ${String(r.body).slice(0, 300)}`);
    }
  });
}

/**
 * 계정을 만든다. 가입은 gateway의 엄격한 한도(IP마다 1분에 5회)를 받으므로 계정마다 다른 클라이언트 주소를 쓴다.
 * @param {number} offset 클라이언트 주소 번호의 시작. 시나리오끼리 겹치지 않게 준다
 */
export function signUp(tag, prefix, count, offset) {
  const users = [];
  for (let i = 0; i < count; i++) {
    users.push({ email: `${prefix}-${i}-${tag}@load.openmarket.local`, password: PASSWORD, address: clientAddress(offset + i) });
  }
  chunks(users).forEach((group) => {
    const responses = http.batch(
      group.map((u) => ({
        method: "POST",
        url: `${env.gateway}/api/auth/v1/accounts`,
        body: JSON.stringify({ email: u.email, password: u.password, name: "부하시험" }),
        params: { headers: { "Content-Type": "application/json", "X-Forwarded-For": u.address }, tags: { name: "seed" } },
      })),
    );
    expectStatus(responses, [201], "가입");
  });
  return users;
}

/** 인증 메일의 토큰으로 이메일을 인증한다. 주문과 입점 신청은 인증한 계정만 된다. */
export function verifyEmails(users) {
  users.forEach((u) => {
    let token;
    for (let attempt = 0; attempt < 60 && !token; attempt++) {
      const search = http.get(`${env.mailpit}/api/v1/search?query=${encodeURIComponent(`to:${u.email}`)}`, { tags: { name: "seed" } });
      const first = search.status === 200 ? search.json("messages.0.ID") : undefined;
      if (first) {
        const text = http.get(`${env.mailpit}/api/v1/message/${first}`, { tags: { name: "seed" } }).json("Text");
        token = (/verify-email\?token=([\w-]+)/.exec(text) || [])[1];
      }
      if (!token) {
        sleep(1);
      }
    }
    if (!token) {
      throw new Error(`${u.email}로 인증 메일이 오지 않았다`);
    }
    const r = http.post(`${env.gateway}/api/auth/v1/accounts/email-verification`, JSON.stringify({ token }), {
      headers: { "Content-Type": "application/json", "X-Forwarded-For": u.address },
      tags: { name: "seed" },
    });
    expectStatus([r], [200, 204], "이메일 인증");
  });
}

/**
 * 사용자마다 로그인해 액세스 토큰을 받는다. auth의 로그인 한도(IP마다 1분에 AUTH_LOGIN_RATE_LIMIT회)는 실제 접속 주소를 보므로
 * run.sh가 한도를 늘려 띄운다.
 * @param {"shop"|"seller"|"admin"} clientName
 * @returns {string[]} users와 같은 순서의 액세스 토큰
 */
export function login(clientName, users) {
  const client = env.clients[clientName];
  const redirectUri = `${client.app}/auth/callback`;
  const tokens = [];
  chunks(users, BATCH).forEach((group) => {
    const sessions = group.map((u) => {
      const verifier = encoding.b64encode(crypto.randomBytes(48), "rawurl");
      const query = [
        "response_type=code",
        `client_id=${client.id}`,
        `scope=${encodeURIComponent("openid profile api")}`,
        `redirect_uri=${encodeURIComponent(redirectUri)}`,
        "state=load",
        `code_challenge=${crypto.sha256(verifier, "base64rawurl")}`,
        "code_challenge_method=S256",
      ].join("&");
      return { user: u, verifier, authorize: `${env.auth}/oauth2/authorize?${query}`, jar: new http.CookieJar() };
    });
    const tag = { name: "seed" };
    // 1. 인가 요청 → 로그인 화면(CSRF 토큰). 인가 요청은 auth 세션에 저장된다.
    const pages = http.batch(sessions.map((s) => ({ method: "GET", url: s.authorize, params: { jar: s.jar, headers: { Accept: "text/html" }, tags: tag } })));
    expectStatus(pages, [200], "로그인 화면");
    // 2. 폼 로그인
    const logins = http.batch(
      sessions.map((s, i) => ({
        method: "POST",
        url: `${env.auth}/login`,
        body: { username: s.user.email, password: s.user.password, _csrf: (/name="_csrf" value="([^"]+)"/.exec(pages[i].body) || [])[1] },
        params: { jar: s.jar, redirects: 0, tags: tag },
      })),
    );
    expectStatus(logins, [302], "로그인");
    // 3. 저장된 인가 요청을 다시 보내 인가 코드를 받는다.
    const codes = http.batch(sessions.map((s) => ({ method: "GET", url: s.authorize, params: { jar: s.jar, redirects: 0, headers: { Accept: "text/html" }, tags: tag } })));
    // 4. 코드를 토큰으로 바꾼다.
    const basic = `Basic ${encoding.b64encode(`${client.id}:${client.secret}`)}`;
    const issued = http.batch(
      sessions.map((s, i) => {
        const code = (/[?&]code=([^&]+)/.exec(codes[i].headers.Location || "") || [])[1];
        if (!code) {
          throw new Error(`인가 코드를 받지 못했다: ${s.user.email} ${codes[i].status} ${codes[i].headers.Location}`);
        }
        return {
          method: "POST",
          url: `${env.auth}/oauth2/token`,
          body: { grant_type: "authorization_code", code: decodeURIComponent(code), redirect_uri: redirectUri, code_verifier: s.verifier },
          params: { headers: { Authorization: basic }, tags: tag },
        };
      }),
    );
    expectStatus(issued, [200], "토큰 발급");
    issued.forEach((r) => tokens.push(r.json("access_token")));
  });
  return tokens;
}

/** 액세스 토큰의 roles 클레임. 서명은 확인하지 않는다. */
export function rolesOf(accessToken) {
  const payload = JSON.parse(encoding.b64decode(accessToken.split(".")[1], "rawurl", "s"));
  return payload.roles || [];
}

export function adminToken() {
  return login("admin", [{ email: env.adminEmail, password: env.adminPassword }])[0];
}
