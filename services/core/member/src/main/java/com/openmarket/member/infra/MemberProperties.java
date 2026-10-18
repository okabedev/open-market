package com.openmarket.member.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * member 설정. 값은 infra/config-repo/member.yml에 둔다.
 *
 * @param accountNumberKey 정산 계좌 번호 암호화 키({@code MEMBER_ACCOUNT_NUMBER_KEY}, .env, Base64 32바이트).
 *     바꾸면 저장된 계좌 번호를 복호화하지 못한다
 */
@ConfigurationProperties("openmarket.member")
public record MemberProperties(String accountNumberKey) {

    /** 비밀 값을 남기지 않는다. */
    @Override
    public String toString() {
        return "MemberProperties[***]";
    }
}
