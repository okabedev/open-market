package com.openmarket.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 토스 결제 API(승인, 조회, 취소)를 흉내 내는 WireMock 서버. 응답 본문은 R9에서 받은 실제 응답을 본떴다(ADR 0018). JVM에서 하나만 띄워 테스트
 * 클래스들이 같이 쓴다. 테스트마다 {@link #reset()}으로 스텁을 지운다.
 */
public final class TossStub {

    public static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    public static final String CONFIRM = "/v1/payments/confirm";

    static {
        SERVER.start();
    }

    private TossStub() {}

    /** 토스 어댑터가 이 서버를 부르게 한다. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("openmarket.payment.toss.base-url", SERVER::baseUrl);
    }

    public static void reset() {
        SERVER.resetAll();
    }

    /** 토스 결제 객체. {@code status}는 토스 결제 상태다. */
    public static String payment(String paymentKey, UUID orderId, long amount, String status) {
        try {
            return Files.readString(Path.of("src/test/resources/toss/payment-done.json"))
                    .replace("${paymentKey}", paymentKey)
                    .replace("${orderId}", orderId.toString())
                    .replace("${status}", status)
                    .replace("${amount}", Long.toString(amount));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 취소된 토스 결제 객체(취소 API 응답). 취소 거래 하나가 {@code cancels}에 있다.
     *
     * @param balanceAmount 취소 뒤 남은 금액. 전액 취소면 0이다
     */
    public static String cancelledPayment(
            String paymentKey, UUID orderId, long amount, String status, long balanceAmount) {
        try {
            return Files.readString(Path.of("src/test/resources/toss/payment-canceled.json"))
                    .replace("${paymentKey}", paymentKey)
                    .replace("${orderId}", orderId.toString())
                    .replace("${status}", status)
                    .replace("${cancelReason}", "주문 취소")
                    .replace("${balanceAmount}", Long.toString(balanceAmount))
                    .replace("${amount}", Long.toString(amount));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String error(String code, String message) {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }

    public static void confirmReturns(int status, String body) {
        SERVER.stubFor(post(urlPathEqualTo(CONFIRM)).willReturn(json(status, body)));
    }

    /** 승인 응답을 {@code delayMillis}만큼 늦게 준다(읽기 타임아웃). */
    public static void confirmDelayed(int delayMillis, String body) {
        SERVER.stubFor(
                post(urlPathEqualTo(CONFIRM)).willReturn(okJson(body).withFixedDelay(delayMillis)));
    }

    public static void findReturns(String paymentKey, int status, String body) {
        SERVER.stubFor(
                get(urlPathEqualTo("/v1/payments/" + paymentKey)).willReturn(json(status, body)));
    }

    public static String cancelPath(String paymentKey) {
        return "/v1/payments/" + paymentKey + "/cancel";
    }

    public static void cancelReturns(String paymentKey, int status, String body) {
        SERVER.stubFor(post(urlPathEqualTo(cancelPath(paymentKey))).willReturn(json(status, body)));
    }

    /** 취소 응답을 {@code delayMillis}만큼 늦게 준다(읽기 타임아웃). */
    public static void cancelDelayed(String paymentKey, int delayMillis, String body) {
        SERVER.stubFor(
                post(urlPathEqualTo(cancelPath(paymentKey)))
                        .willReturn(okJson(body).withFixedDelay(delayMillis)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
            int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
