package com.openmarket.member.command.application;

/** 정산 계좌 번호 암호화(docs/architecture/data.md "개인정보"). 구현은 {@code infra}에 있고 키는 환경 변수다. */
public interface AccountNumberCipherPort {

    String encrypt(String accountNumber);
}
