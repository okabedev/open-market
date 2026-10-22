// Next.js 서버(서버 컴포넌트, Server Action, Route Handler)에서만 쓴다. 클라이언트 컴포넌트에서 불러오면 빌드가 실패한다.
import "server-only";

import path from "node:path";
import { createServiceClient, type ServiceClient, type ServiceName } from "@openmarket/api-client";
import { cookies, headers } from "next/headers";
import { createClient, type RedisClientType } from "redis";
import { createBff, type Bff } from "./bff";
import { loadConfig, type AppName } from "./config";
import { sessionCookieName } from "./cookies";
import { clientAddress } from "./forwarded";
import { createOidcClient } from "./oidc";
import { fetchPrivateFile, privateFilePath, type PrivateFileScope } from "./private-file";
import type { SessionView } from "./session";
import { RedisSessionStore } from "./store";

type RedisClient = RedisClientType;

// 개발 서버는 모듈을 다시 불러오므로 연결은 프로세스에 하나만 두고 globalThis에 보관한다.
const globalForBff = globalThis as typeof globalThis & {
  __openmarketBff?: Map<AppName, Promise<Bff>>;
};

async function connect(url: string): Promise<RedisClient> {
  const redis = createClient({ url });
  redis.on("error", (error: Error) => {
    console.error(`[bff] Redis 오류: ${error.message}`);
  });
  await redis.connect();
  return redis;
}

/**
 * 비밀 값(`<앱>_CLIENT_SECRET` 등)은 레포 루트 `.env` 하나에 둔다(docs/dev-setup.md). 앱은 `clients/apps/<앱>`에서 뜨므로 세 단계
 * 위를 읽는다. 셸이나 컨테이너가 준 환경 변수는 덮어쓰지 않고, 파일이 없으면 넘어간다.
 */
function loadRootEnv(): void {
  try {
    process.loadEnvFile(path.resolve(process.cwd(), "../../../.env"));
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
      throw error;
    }
  }
}

function instance(app: AppName): Promise<Bff> {
  const instances = (globalForBff.__openmarketBff ??= new Map());
  let bff = instances.get(app);
  if (!bff) {
    loadRootEnv();
    const { bff: config, oidc, redisUrl } = loadConfig(app);
    bff = connect(redisUrl)
      .then((redis) =>
        createBff({
          config,
          store: new RedisSessionStore(redis, app),
          oidc: createOidcClient(oidc),
        }),
      )
      .catch((error: unknown) => {
        instances.delete(app);
        throw error;
      });
    instances.set(app, bff);
  }
  return bff;
}

export type NextBff = {
  /** `app/auth/{login,callback,logout}/route.ts`가 내보낸다. login·callback은 GET, logout은 POST */
  handlers: {
    login(request: Request): Promise<Response>;
    callback(request: Request): Promise<Response>;
    logout(request: Request): Promise<Response>;
  };
  /** 로그인한 사용자. 토큰은 넣지 않는다 */
  getSession(): Promise<SessionView | null>;
  /** gateway에 보낼 액세스 토큰. 만료가 가까우면 갱신한다. 로그인하지 않았거나 세션이 끝났으면 null */
  getAccessToken(): Promise<string | null>;
  /** 사용자 토큰을 붙인 서비스 클라이언트. 로그인하지 않았으면 토큰 없이 만든다(공개 경로만 부를 수 있다) */
  serviceClient<S extends ServiceName>(service: S): Promise<ServiceClient<S>>;
  /** 토큰 없는 서비스 클라이언트. 가입처럼 로그인 전에 부르는 공개 경로용 */
  publicClient<S extends ServiceName>(service: S): Promise<ServiceClient<S>>;
  /**
   * 비공개 파일(클레임 사진) 변형을 사용자 토큰으로 받아 넘긴다. 이미지 Route Handler가 그대로 돌려준다. 잘못된 값, 로그인 전, 볼 수
   * 없는 파일은 404다
   */
  privateFile(scope: PrivateFileScope, fileId: string, variant: string): Promise<Response>;
};

/** 두 클라이언트 모두 브라우저 주소를 `X-Forwarded-For`로 넘긴다. gateway가 IP별 레이트 리밋을 그 값으로 센다. */
async function forwardedFor(): Promise<{ forwardedFor?: string }> {
  const address = clientAddress((await headers()).get("x-forwarded-for"));
  return address ? { forwardedFor: address } : {};
}

/** 앱 하나의 BFF. 앱의 `lib/bff.ts`에서 한 번 만든다. */
export function createNextBff(app: AppName): NextBff {
  const sid = async () => (await cookies()).get(sessionCookieName(app))?.value;
  const getAccessToken = async () => (await instance(app)).getAccessToken(await sid());
  return {
    handlers: {
      login: async (request) => (await instance(app)).login(request),
      callback: async (request) => (await instance(app)).callback(request),
      logout: async (request) => (await instance(app)).logout(request),
    },
    getSession: async () => (await instance(app)).getSession(await sid()),
    getAccessToken,
    async serviceClient(service) {
      const accessToken = await getAccessToken();
      return createServiceClient(service, {
        ...(await forwardedFor()),
        ...(accessToken ? { accessToken } : {}),
      });
    },
    async publicClient(service) {
      return createServiceClient(service, await forwardedFor());
    },
    async privateFile(scope, fileId, variant) {
      const path = privateFilePath(scope, fileId, variant);
      if (!path) {
        return new Response(null, { status: 404 });
      }
      return fetchPrivateFile(path, {
        accessToken: await getAccessToken(),
        ...(await forwardedFor()),
      });
    },
  };
}
