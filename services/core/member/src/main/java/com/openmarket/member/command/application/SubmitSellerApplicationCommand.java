package com.openmarket.member.command.application;

import java.util.UUID;

/**
 * 입점 신청(SEL-1).
 *
 * @param businessNumber 사업자 등록 번호. 하이픈은 있어도 없어도 된다
 * @param accountNumber 정산 계좌 번호. 암호화해 저장하고 응답에는 마스킹한 값만 준다
 */
public record SubmitSellerApplicationCommand(
        UUID memberId,
        String storeName,
        String businessNumber,
        String representativeName,
        String bankName,
        String accountHolder,
        String accountNumber) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SubmitSellerApplicationCommand[memberId=" + memberId + "]";
    }
}
