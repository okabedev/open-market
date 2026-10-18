/** Redis에 두는 BFF 세션. 토큰은 여기에만 있고 브라우저에는 세션 ID 쿠키만 간다. */
export type Session = {
  sub: string;
  roles: string[];
  accessToken: string;
  /** epoch ms */
  accessTokenExpiresAt: number;
  refreshToken: string;
  idToken: string;
  /** epoch ms */
  createdAt: number;
};

/** 화면에 넘겨도 되는 세션 정보. 토큰을 넣지 않는다. */
export type SessionView = {
  sub: string;
  roles: string[];
};

/** 로그인 시작부터 콜백까지 들고 있는 값. state를 키로 저장한다. */
export type LoginState = {
  codeVerifier: string;
  nonce: string;
  returnTo: string;
};
