package com.openmarket.notification.command.application;

import java.util.Optional;
import java.util.UUID;

/** 회원의 알림 이메일 주소를 member에서 조회한다(내부 API, OpenFeign). 주소는 로그에 남기지 않는다. */
public interface MemberContactPort {

    /**
     * 인증한 이메일 주소. 회원이 없거나 탈퇴했거나 이메일을 인증하지 않았으면 비어 있다.
     *
     * @throws ContactUnavailableException member를 부를 수 없다(타임아웃, 5xx, 서킷 열림). 다시 시도할 수 있다
     */
    Optional<String> verifiedEmail(UUID memberId);

    /** member를 부를 수 없다. 메시지에 회원 정보를 넣지 않는다. */
    class ContactUnavailableException extends RuntimeException {

        public ContactUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
