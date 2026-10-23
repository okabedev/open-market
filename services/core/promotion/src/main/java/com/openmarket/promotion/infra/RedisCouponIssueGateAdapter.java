package com.openmarket.promotion.infra;

import com.openmarket.promotion.command.application.CouponIssueGatePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 선착순 발급 게이트의 Redis 구현. 쿠폰마다 발급 수 {@code promotion:coupon-stock:<couponId>}와 발급한 회원 집합 {@code
 * promotion:coupon-issued:<couponId>}를 두고, 확인과 차지를 Lua 스크립트 하나로 원자적으로 한다(docs/architecture/data.md
 * "Redis").
 *
 * <p>발급 수 키가 있으면 "키를 만들었다"는 뜻이다. 키를 만들 때는 회원 집합을 먼저 채우고, 발급 수를 두는 것과 차지를 마지막 스크립트 하나로 한다. 그 사이 집합이
 * 지워져도(Redis 재시작이 겹침) DB 유니크가 1인 1매를 지킨다.
 */
@Component
public class RedisCouponIssueGateAdapter implements CouponIssueGatePort {

    private static final Logger log = LoggerFactory.getLogger(RedisCouponIssueGateAdapter.class);

    /** 키를 만들 때 SADD 한 번에 넣는 회원 수. Lua 스택 한도(약 8000개)보다 작게 둔다. */
    static final int LOAD_CHUNK = 1000;

    // ARGV: 1 회원, 2 수량, 3 키 만료(epoch 초). 1 차지함, 0 이미 집합에 있음, -1 매진
    private static final String CLAIM_BODY =
            """
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 0 end
            if tonumber(redis.call('GET', KEYS[1])) >= tonumber(ARGV[2]) then return -1 end
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('INCR', KEYS[1])
            redis.call('EXPIREAT', KEYS[1], ARGV[3])
            redis.call('EXPIREAT', KEYS[2], ARGV[3])
            return 1
            """;

    // -2 키 없음
    private static final RedisScript<Long> CLAIM =
            RedisScript.of(
                    "if redis.call('EXISTS', KEYS[1]) == 0 then return -2 end\n" + CLAIM_BODY,
                    Long.class);

    // ARGV 4: DB 발급 수. 발급 수 키가 없을 때만 둔다.
    private static final RedisScript<Long> LOAD_AND_CLAIM =
            RedisScript.of(
                    "if redis.call('EXISTS', KEYS[1]) == 0 then redis.call('SET', KEYS[1],"
                            + " ARGV[4], 'EXAT', ARGV[3]) end\n"
                            + CLAIM_BODY,
                    Long.class);

    // ARGV: 1 키 만료, 2.. 회원. 발급 수 키가 이미 있으면(다른 요청이 만듦) 0
    private static final RedisScript<Long> LOAD_MEMBERS =
            RedisScript.of(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
                    redis.call('SADD', KEYS[2], unpack(ARGV, 2))
                    redis.call('EXPIREAT', KEYS[2], ARGV[1])
                    return 1
                    """,
                    Long.class);

    // ARGV 1: 회원(빈 문자열이면 발급 수만 내린다)
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(
                    """
                    if ARGV[1] ~= '' then redis.call('SREM', KEYS[2], ARGV[1]) end
                    local count = tonumber(redis.call('GET', KEYS[1]))
                    if count and count > 0 then redis.call('DECR', KEYS[1]) end
                    return 1
                    """,
                    Long.class);

    // ARGV 1: 수량
    private static final RedisScript<Long> MARK_SOLD_OUT =
            RedisScript.of(
                    """
                    local count = tonumber(redis.call('GET', KEYS[1]))
                    if count and count < tonumber(ARGV[1]) then
                      redis.call('SET', KEYS[1], ARGV[1], 'KEEPTTL')
                    end
                    return 1
                    """,
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisCouponIssueGateAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Claim claim(UUID couponId, UUID memberId, int quantity, Instant keysExpireAt) {
        try {
            return toClaim(
                    redis.execute(
                            CLAIM,
                            keys(couponId),
                            memberId.toString(),
                            Integer.toString(quantity),
                            epochSecond(keysExpireAt)));
        } catch (DataAccessException e) {
            throw CouponIssueGatePort.unavailable(e);
        }
    }

    @Override
    public Claim loadAndClaim(
            UUID couponId,
            Collection<UUID> issuedMemberIds,
            UUID memberId,
            int quantity,
            Instant keysExpireAt) {
        String expireAt = epochSecond(keysExpireAt);
        List<String> members = issuedMemberIds.stream().map(UUID::toString).toList();
        try {
            for (int from = 0; from < members.size(); from += LOAD_CHUNK) {
                List<String> args = new ArrayList<>();
                args.add(expireAt);
                args.addAll(members.subList(from, Math.min(from + LOAD_CHUNK, members.size())));
                Long loaded = redis.execute(LOAD_MEMBERS, keys(couponId), args.toArray());
                if (loaded != null && loaded == 0) {
                    // 다른 요청이 이미 키를 다 만들었다.
                    break;
                }
            }
            return toClaim(
                    redis.execute(
                            LOAD_AND_CLAIM,
                            keys(couponId),
                            memberId.toString(),
                            Integer.toString(quantity),
                            expireAt,
                            Integer.toString(members.size())));
        } catch (DataAccessException e) {
            throw CouponIssueGatePort.unavailable(e);
        }
    }

    @Override
    public void release(UUID couponId, UUID memberId) {
        runQuietly("release", couponId, RELEASE, memberId.toString());
    }

    @Override
    public void releaseStock(UUID couponId) {
        runQuietly("releaseStock", couponId, RELEASE, "");
    }

    @Override
    public void markSoldOut(UUID couponId, int quantity) {
        runQuietly("markSoldOut", couponId, MARK_SOLD_OUT, Integer.toString(quantity));
    }

    /** 되돌리기는 실패해도 요청을 실패시키지 않는다. 남는 차이는 Redis가 많게 세는 쪽이라 초과 발급이 되지 않는다. */
    private void runQuietly(String operation, UUID couponId, RedisScript<Long> script, String arg) {
        try {
            redis.execute(script, keys(couponId), arg);
        } catch (DataAccessException e) {
            log.warn("쿠폰 발급 게이트 {} 실패: couponId={}", operation, couponId, e);
        }
    }

    private static Claim toClaim(@Nullable Long result) {
        return switch (Objects.requireNonNull(result).intValue()) {
            case 1 -> Claim.CLAIMED;
            case 0 -> Claim.ALREADY_CLAIMED;
            case -1 -> Claim.SOLD_OUT;
            case -2 -> Claim.NOT_LOADED;
            default -> throw new IllegalStateException("알 수 없는 발급 스크립트 결과: " + result);
        };
    }

    private static String epochSecond(Instant instant) {
        return Long.toString(instant.getEpochSecond());
    }

    static String stockKey(UUID couponId) {
        return "promotion:coupon-stock:" + couponId;
    }

    static String issuedKey(UUID couponId) {
        return "promotion:coupon-issued:" + couponId;
    }

    private static List<String> keys(UUID couponId) {
        return List.of(stockKey(couponId), issuedKey(couponId));
    }
}
