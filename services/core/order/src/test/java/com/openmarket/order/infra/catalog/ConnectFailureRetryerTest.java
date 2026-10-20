package com.openmarket.order.infra.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.order.infra.catalog.CatalogClientConfiguration.ConnectFailureRetryer;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectFailureRetryerTest {

    final Retryer retryer = new CatalogClientConfiguration().feignRetryer().clone();

    @Test
    @DisplayName("연결 실패는 한 번만 다시 시도한다")
    void retriesConnectFailureOnce() {
        RetryableException e = retryable(new ConnectException("refused"));

        assertThatCode(() -> retryer.continueOrPropagate(e)).doesNotThrowAnyException();
        assertThatThrownBy(() -> retryer.continueOrPropagate(e)).isSameAs(e);
    }

    @Test
    @DisplayName("읽기 타임아웃은 다시 시도하지 않는다")
    void doesNotRetryReadTimeout() {
        RetryableException e = retryable(new SocketTimeoutException("Read timed out"));

        assertThatThrownBy(() -> retryer.continueOrPropagate(e)).isSameAs(e);
    }

    @Test
    @DisplayName("clone은 시도 횟수를 새로 센다")
    void cloneResetsAttempts() {
        RetryableException e = retryable(new ConnectException("refused"));
        retryer.continueOrPropagate(e);

        assertThatCode(() -> retryer.clone().continueOrPropagate(e)).doesNotThrowAnyException();
        assertThatCode(() -> new ConnectFailureRetryer(new Retryer.Default()).clone())
                .doesNotThrowAnyException();
    }

    private static RetryableException retryable(Exception cause) {
        Request request =
                Request.create(
                        Request.HttpMethod.GET,
                        "http://catalog/internal/v1/skus",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        return new RetryableException(
                -1, cause.getMessage(), Request.HttpMethod.GET, cause, (Long) null, request);
    }
}
