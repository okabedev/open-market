package com.openmarket.common.messaging.deadletter;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLT 메시지 관리자 API(docs/architecture/messaging.md "DLT 재처리"). 서비스마다 같은 경로다. {@code /v1/admin/**}의
 * ADMIN 역할 검사는 서비스의 보안 설정이 한다(common-security, auth는 자기 설정).
 */
@RestController
@RequestMapping("/v1/admin/dead-letters")
public class DeadLetterAdminController {

    private final DeadLetterService deadLetters;

    public DeadLetterAdminController(DeadLetterService deadLetters) {
        this.deadLetters = deadLetters;
    }

    /** 쌓인 시각 내림차순 목록. 커서로 넘긴다. */
    @GetMapping
    public DeadLetterPage listDeadLetters(
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable String topic,
            @RequestParam(required = false) @Nullable String before,
            @RequestParam(defaultValue = "20") int size) {
        return deadLetters.list(status, topic, before, size);
    }

    /** 원래 토픽으로 실패한 그룹에만 다시 보낸다. 204. 이미 처리한 메시지면 409 {@code DEAD_LETTER_NOT_PENDING}. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retryDeadLetter(
            @PathVariable UUID id, @Nullable Principal principal) {
        deadLetters.retry(id, actor(principal));
        return ResponseEntity.noContent().build();
    }

    /** 다시 처리하지 않기로 한다. 사유가 필요하다. 204. */
    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discardDeadLetter(
            @PathVariable UUID id,
            @RequestBody DiscardDeadLetterRequest request,
            @Nullable Principal principal) {
        deadLetters.discard(id, actor(principal), request.reason());
        return ResponseEntity.noContent().build();
    }

    private static String actor(@Nullable Principal principal) {
        if (principal == null) {
            throw new BusinessException(
                    ErrorType.UNAUTHORIZED, "COMMON_UNAUTHORIZED", "로그인이 필요합니다.");
        }
        return principal.getName();
    }

    /**
     * 버리기 본문.
     *
     * @param reason 버리는 사유. 1~500자
     */
    public record DiscardDeadLetterRequest(@Nullable String reason) {}
}
