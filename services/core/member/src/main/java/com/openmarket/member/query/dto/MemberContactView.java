package com.openmarket.member.query.dto;

/**
 * 알림을 보낼 연락처(notification이 부른다). 이메일만 주고 이름·전화번호는 주지 않는다.
 *
 * @param emailVerified 이메일 인증을 마쳤는가. notification은 인증한 주소에만 보낸다
 */
public record MemberContactView(String email, boolean emailVerified) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "MemberContactView[emailVerified=" + emailVerified + "]";
    }
}
