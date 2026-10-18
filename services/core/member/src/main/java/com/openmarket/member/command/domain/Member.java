package com.openmarket.member.command.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 집계: 프로필(이름, 연락처)과 배송지(docs/glossary.md "회원", "배송지"). {@code id}는 auth의 accountId와 같다.
 *
 * <p>회원은 auth의 {@code AccountRegistered}로 만들어진다. 이메일은 auth가 소유하고 여기에는 연락처로 복사해 둔다.
 *
 * <p>배송지는 {@value #MAX_ADDRESSES}개까지이고, 하나라도 있으면 기본 배송지가 꼭 하나다(ACC-3). 기본 배송지는 배송지의 표시가 아니라 회원의
 * {@code defaultAddressId}로 둬서 두 개가 될 수 없게 한다.
 */
@Entity
@Table(name = "member")
public class Member {

    public static final int MAX_ADDRESSES = 10;

    @Id private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column private @Nullable String phone;

    @Column(name = "email_verified_at")
    private @Nullable Instant emailVerifiedAt;

    @Column(name = "default_address_id")
    private @Nullable UUID defaultAddressId;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<Address> addresses = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Member() {}

    private Member(UUID id, String email, String name, Instant now) {
        this.id = id;
        this.email = email;
        this.name = ContactRules.name(name);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** auth에서 가입한 계정의 회원을 만든다. */
    public static Member register(UUID id, String email, String name, Instant now) {
        return new Member(id, email, name, now);
    }

    /** 이메일 인증을 기록한다. 처음 바뀌었으면 {@code true}다. */
    public boolean verifyEmail(Instant now) {
        if (emailVerifiedAt != null) {
            return false;
        }
        emailVerifiedAt = now;
        updatedAt = now;
        return true;
    }

    /** 이름과 연락처를 바꾼다. 연락처는 {@code null}이나 빈 값이면 지운다. */
    public void changeProfile(String name, @Nullable String phone, Instant now) {
        this.name = ContactRules.name(name);
        this.phone = ContactRules.optionalPhone(phone);
        updatedAt = now;
    }

    /** 배송지를 더한다. 첫 배송지이거나 {@code asDefault}면 기본 배송지가 된다. */
    public Address addAddress(
            UUID addressId, Address.Details details, boolean asDefault, Instant now) {
        if (addresses.size() >= MAX_ADDRESSES) {
            throw new AddressLimitExceededException();
        }
        Address address = new Address(addressId, this, details, now);
        addresses.add(address);
        if (asDefault || defaultAddressId == null) {
            defaultAddressId = addressId;
        }
        updatedAt = now;
        return address;
    }

    public void changeAddress(UUID addressId, Address.Details details, Instant now) {
        address(addressId).change(details, now);
        updatedAt = now;
    }

    public void makeDefaultAddress(UUID addressId, Instant now) {
        address(addressId);
        if (!addressId.equals(defaultAddressId)) {
            defaultAddressId = addressId;
            updatedAt = now;
        }
    }

    /** 배송지를 지운다. 기본 배송지였으면 남은 것 중 가장 최근에 추가한 배송지가 기본이 된다. 없는 배송지면 아무것도 하지 않는다. */
    public void removeAddress(UUID addressId, Instant now) {
        if (!addresses.removeIf(address -> address.id().equals(addressId))) {
            return;
        }
        if (addressId.equals(defaultAddressId)) {
            defaultAddressId = addresses.isEmpty() ? null : addresses.getLast().id();
        }
        updatedAt = now;
    }

    private Address address(UUID addressId) {
        return addresses.stream()
                .filter(address -> address.id().equals(addressId))
                .findFirst()
                .orElseThrow(AddressNotFoundException::new);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public @Nullable String phone() {
        return phone;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public @Nullable UUID defaultAddressId() {
        return defaultAddressId;
    }

    public List<Address> addresses() {
        return Collections.unmodifiableList(addresses);
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "Member[id=" + id + "]";
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Member member && Objects.equals(id, member.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
