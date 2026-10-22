package com.openmarket.notification.infra.member;

import feign.RetryableException;
import feign.Retryer;
import java.net.ConnectException;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * member 연락처 조회 클라이언트(docs/architecture/notification.md "이메일 받는 주소"). 타임아웃과 서킷 브레이커 값은 config-repo
 * notification.yml에 있다. 규칙은 order의 catalog 조회와 같다(docs/architecture/order.md "catalog 조회").
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "com.openmarket.notification.infra.member")
public class MemberClientConfiguration {

    /**
     * 서비스 간 토큰 관리자. 토큰을 만료 전까지 메모리에 두고 재사용한다. OpenFeign의 {@code spring.cloud.openfeign.oauth2}가 쓴다.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
    }

    /** 연결이 거절됐을 때만 한 번 더 시도한다(조회라 안전하다). 연결·읽기 타임아웃은 다시 시도하지 않는다. */
    @Bean
    Retryer feignRetryer() {
        return new ConnectFailureRetryer(new Retryer.Default(100, 1000, 2));
    }

    /** 원인이 연결 실패({@link ConnectException})인 예외만 {@code delegate}에 넘겨 다시 시도한다. */
    record ConnectFailureRetryer(Retryer delegate) implements Retryer {

        @Override
        public void continueOrPropagate(RetryableException e) {
            if (!(e.getCause() instanceof ConnectException)) {
                throw e;
            }
            delegate.continueOrPropagate(e);
        }

        @Override
        public Retryer clone() {
            return new ConnectFailureRetryer(delegate.clone());
        }
    }
}
