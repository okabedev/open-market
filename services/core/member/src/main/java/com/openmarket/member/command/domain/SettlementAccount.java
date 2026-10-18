package com.openmarket.member.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 정산 계좌(docs/glossary.md "정산 계좌"). 계좌 번호는 원문으로 갖지 않는다. 유스케이스가 암호화한 값과 마스킹한 값을 넘긴다.
 *
 * <p>원문 계좌 번호는 정산 지급(M5)에서 복호화해 쓴다.
 */
@Embeddable
public class SettlementAccount {

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{10,16}");

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    @Column(name = "account_number_encrypted", nullable = false)
    private String accountNumberEncrypted;

    @Column(name = "account_number_masked", nullable = false)
    private String accountNumberMasked;

    protected SettlementAccount() {}

    /**
     * @param accountNumberEncrypted {@link #normalizeNumber}로 정리한 번호를 암호화한 값
     * @param accountNumberMasked 정리한 번호를 마스킹한 값. 응답에는 이 값만 준다
     */
    public SettlementAccount(
            String bankName,
            String accountHolder,
            String accountNumberEncrypted,
            String accountNumberMasked) {
        this.bankName = ContactRules.requiredText(bankName, "은행", 20);
        this.accountHolder = ContactRules.name(accountHolder);
        this.accountNumberEncrypted = accountNumberEncrypted;
        this.accountNumberMasked = accountNumberMasked;
    }

    /** 하이픈과 공백을 지운다. 숫자 10~16자리여야 한다. 오류 메시지에 번호를 넣지 않는다. */
    public static String normalizeNumber(@Nullable String accountNumber) {
        String digits = accountNumber == null ? "" : accountNumber.replaceAll("[\\s-]", "");
        if (!ACCOUNT_NUMBER.matcher(digits).matches()) {
            throw new InvalidMemberInputException("계좌 번호는 숫자 10~16자리입니다.");
        }
        return digits;
    }

    SettlementAccount copy() {
        return new SettlementAccount(
                bankName, accountHolder, accountNumberEncrypted, accountNumberMasked);
    }

    public String bankName() {
        return bankName;
    }

    public String accountHolder() {
        return accountHolder;
    }

    public String accountNumberEncrypted() {
        return accountNumberEncrypted;
    }

    public String accountNumberMasked() {
        return accountNumberMasked;
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SettlementAccount[***]";
    }
}
