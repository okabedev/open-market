package com.openmarket.payment.infra.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openmarket.payment.command.application.PaymentGatewayPort;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 토스페이먼츠 결제 API 어댑터(ACL, ADR 0018). 토스 응답을 {@link PaymentGatewayPort}의 네 가지 결과로 바꾼다. 해석 규칙은
 * docs/architecture/payment.md "승인", "조회 결과". 응답 원문과 시크릿 키는 로그에 남기지 않는다.
 */
@Component
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final String DEFAULT_REJECT_MESSAGE = "결제가 완료되지 않았습니다.";

    private final RestClient client;

    public TossPaymentGatewayAdapter(RestClient.Builder builder, TossProperties properties) {
        // 평문 HTTP(가짜 PG, WireMock)에서 HTTP/2 업그레이드(h2c)를 시도하지 않게 HTTP/1.1로 둔다.
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(properties.connectTimeout())
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        String credentials =
                Base64.getEncoder()
                        .encodeToString(
                                (properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.client =
                builder.clone()
                        .baseUrl(properties.baseUrl().toString())
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                        .build();
    }

    @Override
    public Result confirm(String paymentKey, UUID orderId, long amount, String idempotencyKey) {
        return call(
                () ->
                        client.post()
                                .uri("/v1/payments/confirm")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        Map.of(
                                                "paymentKey", paymentKey,
                                                "orderId", orderId.toString(),
                                                "amount", amount))
                                .exchange(
                                        (request, response) -> {
                                            HttpStatusCode status = response.getStatusCode();
                                            if (status.is2xxSuccessful()) {
                                                return payment(response.bodyTo(TossPayment.class));
                                            }
                                            TossError error = response.bodyTo(TossError.class);
                                            return confirmError(status, error);
                                        }));
    }

    @Override
    public Result find(String paymentKey) {
        return call(
                () ->
                        client.get()
                                .uri("/v1/payments/{paymentKey}", paymentKey)
                                .exchange(
                                        (request, response) -> {
                                            HttpStatusCode status = response.getStatusCode();
                                            if (status.is2xxSuccessful()) {
                                                return payment(response.bodyTo(TossPayment.class));
                                            }
                                            TossError error = response.bodyTo(TossError.class);
                                            if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                                                // 토스에 결제가 없다. 돈이 빠져나가지 않았다.
                                                return new Rejected(
                                                        code(status, error), message(error));
                                            }
                                            return new Unknown(code(status, error));
                                        }));
    }

    private static Result call(Call call) {
        try {
            Result result = call.run();
            return result == null ? new Unknown("EMPTY_RESPONSE") : result;
        } catch (ResourceAccessException e) {
            return new Unknown(ioReason(e.getCause()));
        }
    }

    /** 승인 오류. 토스가 이미 처리했을 수 있는 응답은 실패로 보지 않는다(ADR 0018). */
    private static Result confirmError(HttpStatusCode status, @Nullable TossError error) {
        String code = code(status, error);
        if (status.is5xxServerError()
                || error == null
                || error.code() == null
                || "ALREADY_PROCESSED_PAYMENT".equals(code)
                || "IDEMPOTENT_REQUEST_PROCESSING".equals(code)) {
            return new Unknown(code);
        }
        return new Rejected(code, message(error));
    }

    /** 토스 결제 객체의 상태로 결과를 정한다. */
    private static Result payment(@Nullable TossPayment payment) {
        if (payment == null || payment.status() == null) {
            return new Unknown("INVALID_RESPONSE");
        }
        String status = payment.status();
        return switch (status) {
            case "DONE", "CANCELED", "PARTIAL_CANCELED" -> {
                if (payment.totalAmount() == null
                        || payment.method() == null
                        || payment.approvedAt() == null) {
                    yield new Unknown("INVALID_RESPONSE");
                }
                yield new Approved(
                        status,
                        payment.totalAmount(),
                        payment.method(),
                        payment.approvedAt().toInstant(),
                        payment.lastTransactionKey());
            }
            case "ABORTED", "EXPIRED" -> new Rejected(status, DEFAULT_REJECT_MESSAGE);
            case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" -> new Pending(status);
            default -> new Unknown(status);
        };
    }

    private static String code(HttpStatusCode status, @Nullable TossError error) {
        return error != null && error.code() != null ? error.code() : "HTTP_" + status.value();
    }

    private static String message(@Nullable TossError error) {
        return error != null && error.message() != null ? error.message() : DEFAULT_REJECT_MESSAGE;
    }

    private static String ioReason(@Nullable Throwable cause) {
        if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
            return "CONNECTION_FAILED";
        }
        if (cause instanceof HttpTimeoutException) {
            return "TIMEOUT";
        }
        return cause instanceof IOException ? "IO_ERROR" : "UNKNOWN_ERROR";
    }

    @FunctionalInterface
    private interface Call {
        @Nullable Result run();
    }

    /** 토스 결제 객체에서 쓰는 필드. 결제수단 상세는 읽지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPayment(
            @Nullable String status,
            @Nullable Long totalAmount,
            @Nullable String method,
            @Nullable OffsetDateTime approvedAt,
            @Nullable String lastTransactionKey) {}

    /** 토스 오류 응답. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(@Nullable String code, @Nullable String message) {}
}
