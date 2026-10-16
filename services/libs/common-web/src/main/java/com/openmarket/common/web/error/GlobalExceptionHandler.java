package com.openmarket.common.web.error;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 RFC 9457 ProblemDetail로 응답한다. 확장 필드는 {@code code}, {@code traceId}, {@code errors}다.
 *
 * <p>서비스에는 예외 핸들러를 따로 두지 않는다. 도메인은 {@link BusinessException}을 던지고 HTTP 상태는 여기서 정한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String SECURITY_PACKAGE = "org.springframework.security.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception) {
        HttpStatus status = toStatus(exception.getType());
        if (status.is5xxServerError()) {
            log.warn("{}: {}", exception.getCode(), exception.getMessage(), exception);
        } else {
            log.debug("{}: {}", exception.getCode(), exception.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        enrich(problem, exception.getCode());
        return ResponseEntity.status(status).body(problem);
    }

    /** 처리하지 못한 예외. 내부 메시지는 응답에 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) throws Exception {
        if (isSecurityException(exception)) {
            // 인증·인가 예외는 Spring Security 필터가 401·403으로 바꾸도록 다시 던진다.
            throw exception;
        }
        log.error("처리하지 못한 예외", exception);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");
        enrich(problem, "COMMON_INTERNAL_ERROR");
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setDetail("요청 값이 올바르지 않습니다.");
        List<Map<String, String>> errors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        Map.of(
                                                "field", error.getField(),
                                                "message",
                                                        Objects.requireNonNullElse(
                                                                error.getDefaultMessage(), "")))
                        .toList();
        problem.setProperty("errors", errors);
        enrich(problem, "COMMON_INVALID_REQUEST");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Spring MVC 표준 예외(404, 405, 415 등)에도 오류 코드를 붙인다. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && !hasCode(problem)) {
            enrich(problem, defaultCode(statusCode));
        }
        return response;
    }

    static HttpStatus toStatus(ErrorType type) {
        return switch (type) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "COMMON_INVALID_REQUEST";
            case 401 -> "COMMON_UNAUTHORIZED";
            case 403 -> "COMMON_FORBIDDEN";
            case 404 -> "COMMON_NOT_FOUND";
            case 405 -> "COMMON_METHOD_NOT_ALLOWED";
            case 406 -> "COMMON_NOT_ACCEPTABLE";
            case 409 -> "COMMON_CONFLICT";
            case 413 -> "COMMON_PAYLOAD_TOO_LARGE";
            case 415 -> "COMMON_UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "COMMON_UNPROCESSABLE";
            case 503 -> "COMMON_UNAVAILABLE";
            default ->
                    status.is5xxServerError()
                            ? "COMMON_INTERNAL_ERROR"
                            : "COMMON_HTTP_" + status.value();
        };
    }

    private static boolean hasCode(ProblemDetail problem) {
        @Nullable Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static void enrich(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }

    private static boolean isSecurityException(Exception exception) {
        for (Class<?> type = exception.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith(SECURITY_PACKAGE)) {
                return true;
            }
        }
        return false;
    }
}
