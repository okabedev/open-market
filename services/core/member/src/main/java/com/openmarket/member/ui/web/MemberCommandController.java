package com.openmarket.member.ui.web;

import static com.openmarket.member.ui.web.MemberQueryController.memberId;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.member.command.application.AddAddressCommand;
import com.openmarket.member.command.application.AddAddressUseCase;
import com.openmarket.member.command.application.ChangeAddressCommand;
import com.openmarket.member.command.application.ChangeAddressUseCase;
import com.openmarket.member.command.application.ChangeProfileCommand;
import com.openmarket.member.command.application.ChangeProfileUseCase;
import com.openmarket.member.command.application.RemoveAddressCommand;
import com.openmarket.member.command.application.RemoveAddressUseCase;
import com.openmarket.member.command.application.SetDefaultAddressCommand;
import com.openmarket.member.command.application.SetDefaultAddressUseCase;
import com.openmarket.member.command.application.WithdrawMemberCommand;
import com.openmarket.member.command.application.WithdrawMemberUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 프로필과 배송지 변경(ACC-3). 주체는 토큰의 {@code sub}(= memberId)다. 입력 규칙 위반은 400 {@code
 * MEMBER_INVALID_INPUT}.
 */
@RestController
@RequestMapping("/v1/members/me")
public class MemberCommandController {

    private final ChangeProfileUseCase changeProfile;
    private final AddAddressUseCase addAddress;
    private final ChangeAddressUseCase changeAddress;
    private final SetDefaultAddressUseCase setDefaultAddress;
    private final RemoveAddressUseCase removeAddress;
    private final WithdrawMemberUseCase withdraw;

    public MemberCommandController(
            ChangeProfileUseCase changeProfile,
            AddAddressUseCase addAddress,
            ChangeAddressUseCase changeAddress,
            SetDefaultAddressUseCase setDefaultAddress,
            RemoveAddressUseCase removeAddress,
            WithdrawMemberUseCase withdraw) {
        this.changeProfile = changeProfile;
        this.addAddress = addAddress;
        this.changeAddress = changeAddress;
        this.setDefaultAddress = setDefaultAddress;
        this.removeAddress = removeAddress;
        this.withdraw = withdraw;
    }

    /** 이름과 연락처를 바꾼다. 연락처를 비우면 지운다. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeProfile(
            Principal principal, @Valid @RequestBody ChangeProfileRequest request) {
        changeProfile.execute(
                new ChangeProfileCommand(memberId(principal), request.name(), request.phone()));
    }

    /**
     * 배송지를 더한다. {@code Idempotency-Key}가 필요하다. 첫 배송지는 기본 배송지가 된다. 10개를 넘으면 422 {@code
     * MEMBER_ADDRESS_LIMIT}.
     */
    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public AddAddressResponse addAddress(
            Principal principal, @Valid @RequestBody AddAddressRequest request) {
        return new AddAddressResponse(
                addAddress.execute(
                        new AddAddressCommand(
                                memberId(principal),
                                request.label(),
                                request.recipientName(),
                                request.recipientPhone(),
                                request.zipCode(),
                                request.baseAddress(),
                                request.detailAddress(),
                                Boolean.TRUE.equals(request.asDefault()))));
    }

    /** 배송지 내용을 고친다. 본인 배송지가 아니면 404 {@code MEMBER_ADDRESS_NOT_FOUND}. */
    @PutMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeAddress(
            Principal principal,
            @PathVariable UUID addressId,
            @Valid @RequestBody ChangeAddressRequest request) {
        changeAddress.execute(
                new ChangeAddressCommand(
                        memberId(principal),
                        addressId,
                        request.label(),
                        request.recipientName(),
                        request.recipientPhone(),
                        request.zipCode(),
                        request.baseAddress(),
                        request.detailAddress()));
    }

    /** 기본 배송지로 지정한다. 이미 기본이어도 204다. */
    @PostMapping("/addresses/{addressId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefaultAddress(Principal principal, @PathVariable UUID addressId) {
        setDefaultAddress.execute(new SetDefaultAddressCommand(memberId(principal), addressId));
    }

    /** 배송지를 지운다. 이미 없어도 204다. 기본 배송지였으면 가장 최근에 추가한 배송지가 기본이 된다. */
    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAddress(Principal principal, @PathVariable UUID addressId) {
        removeAddress.execute(new RemoveAddressCommand(memberId(principal), addressId));
    }

    /**
     * 탈퇴한다(ACC-4). 개인정보를 바로 가명 처리하고 auth가 계정을 비활성화한다. 이미 탈퇴했어도 204다. 판매자면 422 {@code
     * MEMBER_SELLER_CANNOT_WITHDRAW}, 진행 중인 주문이 있으면 422 {@code MEMBER_ONGOING_ORDERS}.
     */
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(Principal principal) {
        withdraw.execute(new WithdrawMemberCommand(memberId(principal)));
    }
}
