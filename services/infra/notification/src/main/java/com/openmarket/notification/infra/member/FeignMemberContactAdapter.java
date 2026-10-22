package com.openmarket.notification.infra.member;

import com.openmarket.notification.command.application.MemberContactPort;
import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MemberContactPort}의 OpenFeign 구현. member가 404면(없거나 탈퇴한 회원) 비어 있다. 그 밖의 실패(타임아웃, 5xx, 서킷
 * 열림, 예상하지 못한 4xx)는 {@link MemberContactPort.ContactUnavailableException}이고 발송 작업이 나중에 다시 시도한다.
 */
@Component
class FeignMemberContactAdapter implements MemberContactPort {

    private final MemberContactClient client;

    FeignMemberContactAdapter(MemberContactClient client) {
        this.client = client;
    }

    @Override
    public Optional<String> verifiedEmail(UUID memberId) {
        MemberContactClient.ContactResponse contact;
        try {
            contact = client.contact(memberId);
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw new ContactUnavailableException("member 연락처를 조회하지 못했다: " + rootName(e), e);
        }
        return contact.emailVerified() ? Optional.of(contact.email()) : Optional.empty();
    }

    /** 서킷 브레이커가 감싸도 원인에서 404를 찾는다. */
    private static boolean isNotFound(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException.NotFound) {
                return true;
            }
        }
        return false;
    }

    private static String rootName(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
