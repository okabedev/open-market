package com.openmarket.common.web.testapp;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.common.core.error.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
public class ErrorTestController {

    @GetMapping("/business")
    public void business() {
        throw new BusinessException(ErrorType.UNPROCESSABLE, "TEST_RULE_VIOLATED", "규칙을 어겼습니다.");
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException("TEST_NOT_FOUND", "대상이 없습니다.");
    }

    @GetMapping("/unavailable")
    public void unavailable() {
        throw new BusinessException(
                ErrorType.UNAVAILABLE, "TEST_DOWNSTREAM_DOWN", "잠시 후 다시 시도해 주세요.");
    }

    @GetMapping("/too-many")
    public void tooMany() {
        throw new BusinessException(
                ErrorType.TOO_MANY_REQUESTS, "TEST_QUOTA_EXCEEDED", "한도를 넘었습니다.");
    }

    @GetMapping("/unexpected")
    public void unexpected() {
        throw new IllegalStateException("내부 비밀 정보");
    }

    @PostMapping("/validate")
    public void validate(@Valid @RequestBody CreateRequest request) {}

    public record CreateRequest(@NotBlank String name) {}
}
