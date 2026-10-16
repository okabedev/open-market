package com.openmarket.common.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 중복되면 안 되는 상태 변경 API에 붙인다(NFR-2). 요청에 {@code Idempotency-Key} 헤더가 필수가 된다.
 *
 * <ul>
 *   <li>같은 사용자가 같은 키와 같은 본문으로 다시 보내면 첫 응답(2xx, 4xx)을 그대로 돌려준다.
 *   <li>같은 키에 다른 본문이 오면 422, 첫 요청이 아직 처리 중이면 409를 응답한다.
 *   <li>5xx 응답은 저장하지 않는다. 같은 키로 다시 시도할 수 있다.
 * </ul>
 *
 * <p>기록은 서비스 스키마의 {@code idempotency_record} 테이블에 둔다. DataSource가 없는 앱에서는 동작하지 않는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
