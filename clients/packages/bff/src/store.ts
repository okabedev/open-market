import type { RedisClientType } from "redis";
import type { LoginState, Session } from "./session";

type RedisClient = RedisClientType;

/** 잡은 쪽(토큰이 같은 쪽)만 락을 지운다. */
const UNLOCK_SCRIPT = `if redis.call("get", KEYS[1]) == ARGV[1] then return redis.call("del", KEYS[1]) else return 0 end`;

/**
 * BFF 세션 저장소. 키는 `bff:<앱>:<종류>:<ID>`이고 앱마다 나뉜다.
 * - session: 세션. TTL은 리프레시 토큰 수명과 같다
 * - login: 로그인 진행 상태. 한 번 꺼내면 지운다
 * - lock: 세션별 토큰 갱신 락(R8)
 */
export class RedisSessionStore {
  constructor(
    private readonly redis: RedisClient,
    private readonly app: string,
  ) {}

  async get(sid: string): Promise<Session | null> {
    const value = await this.redis.get(this.key("session", sid));
    return value ? (JSON.parse(value) as Session) : null;
  }

  async save(sid: string, session: Session, ttlSeconds: number): Promise<void> {
    await this.redis.set(this.key("session", sid), JSON.stringify(session), {
      expiration: { type: "EX", value: ttlSeconds },
    });
  }

  async delete(sid: string): Promise<void> {
    await this.redis.del(this.key("session", sid));
  }

  async saveLogin(state: string, login: LoginState, ttlSeconds: number): Promise<void> {
    await this.redis.set(this.key("login", state), JSON.stringify(login), {
      expiration: { type: "EX", value: ttlSeconds },
    });
  }

  async takeLogin(state: string): Promise<LoginState | null> {
    const value = await this.redis.getDel(this.key("login", state));
    return value ? (JSON.parse(value) as LoginState) : null;
  }

  /** 락을 잡으면 true. 잡은 쪽이 죽어도 ttlMs 뒤에 풀린다. */
  async lock(sid: string, owner: string, ttlMs: number): Promise<boolean> {
    const result = await this.redis.set(this.key("lock", sid), owner, {
      expiration: { type: "PX", value: ttlMs },
      condition: "NX",
    });
    return result === "OK";
  }

  async unlock(sid: string, owner: string): Promise<void> {
    await this.redis.eval(UNLOCK_SCRIPT, { keys: [this.key("lock", sid)], arguments: [owner] });
  }

  private key(kind: "session" | "login" | "lock", id: string): string {
    return `bff:${this.app}:${kind}:${id}`;
  }
}

export type SessionStore = Pick<
  RedisSessionStore,
  "get" | "save" | "delete" | "saveLogin" | "takeLogin" | "lock" | "unlock"
>;
