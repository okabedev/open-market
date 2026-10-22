package com.openmarket.member.query.dao;

import static com.openmarket.member.command.domain.QAddress.address;
import static com.openmarket.member.command.domain.QMember.member;

import com.openmarket.member.query.dto.AddressView;
import com.openmarket.member.query.dto.MemberContactView;
import com.openmarket.member.query.dto.MemberView;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MemberQueryDao {

    /** 탈퇴한 회원은 없는 회원처럼 404다. */
    private static final String ACTIVE = "ACTIVE";

    private final JPAQueryFactory query;

    public MemberQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    public Optional<MemberView> findMember(UUID memberId) {
        return Optional.ofNullable(
                query.select(
                                Projections.constructor(
                                        MemberView.class,
                                        member.id,
                                        member.email,
                                        member.name,
                                        member.phone,
                                        member.emailVerifiedAt,
                                        member.createdAt))
                        .from(member)
                        .where(member.id.eq(memberId), member.status.stringValue().eq(ACTIVE))
                        .fetchOne());
    }

    /** 알림을 보낼 연락처. 탈퇴한 회원은 비어 있다(가명 이메일을 주지 않는다). */
    public Optional<MemberContactView> findContact(UUID memberId) {
        return Optional.ofNullable(
                query.select(
                                Projections.constructor(
                                        MemberContactView.class,
                                        member.email,
                                        member.emailVerifiedAt.isNotNull()))
                        .from(member)
                        .where(member.id.eq(memberId), member.status.stringValue().eq(ACTIVE))
                        .fetchOne());
    }

    /** 기본 배송지가 먼저, 나머지는 최근에 추가한 순서. 회원당 10개까지라 페이징하지 않는다. */
    public List<AddressView> findAddresses(UUID memberId) {
        BooleanExpression isDefault =
                new CaseBuilder()
                        .when(address.id.eq(member.defaultAddressId))
                        .then(true)
                        .otherwise(false);
        return query.select(
                        Projections.constructor(
                                AddressView.class,
                                address.id,
                                address.label,
                                address.recipientName,
                                address.recipientPhone,
                                address.zipCode,
                                address.baseAddress,
                                address.detailAddress,
                                isDefault,
                                address.createdAt))
                .from(address)
                .join(address.member, member)
                .where(member.id.eq(memberId))
                .orderBy(isDefault.desc(), address.createdAt.desc(), address.id.desc())
                .fetch();
    }
}
