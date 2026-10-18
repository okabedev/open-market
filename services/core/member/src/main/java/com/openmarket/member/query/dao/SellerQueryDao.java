package com.openmarket.member.query.dao;

import static com.openmarket.member.command.domain.QMember.member;
import static com.openmarket.member.command.domain.QSeller.seller;
import static com.openmarket.member.command.domain.QSellerApplication.sellerApplication;

import com.openmarket.member.query.dto.MySellerApplicationView;
import com.openmarket.member.query.dto.PageView;
import com.openmarket.member.query.dto.SellerApplicationReviewView;
import com.openmarket.member.query.dto.SellerApplicationStatus;
import com.openmarket.member.query.dto.SellerReviewView;
import com.openmarket.member.query.dto.SellerStatus;
import com.openmarket.member.query.dto.SellerView;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class SellerQueryDao {

    private final JPAQueryFactory query;

    public SellerQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 본인 신청 이력. 최근 신청이 먼저다. */
    public List<MySellerApplicationView> myApplications(UUID memberId) {
        return query.select(
                        Projections.constructor(
                                MySellerApplicationView.class,
                                sellerApplication.id,
                                sellerApplication.storeName,
                                sellerApplication.businessNumber,
                                sellerApplication.representativeName,
                                sellerApplication.settlementAccount.bankName,
                                sellerApplication.settlementAccount.accountHolder,
                                sellerApplication.settlementAccount.accountNumberMasked,
                                sellerApplication.status.stringValue(),
                                sellerApplication.rejectReason,
                                sellerApplication.submittedAt,
                                sellerApplication.reviewedAt))
                .from(sellerApplication)
                .where(sellerApplication.memberId.eq(memberId))
                .orderBy(sellerApplication.submittedAt.desc(), sellerApplication.id.desc())
                .fetch();
    }

    /** 관리자 심사 목록. 상태별로 오래된 신청부터(인덱스 {@code (status, submitted_at)}). 마스킹 전 값이다. */
    public PageView<SellerApplicationReviewView> applicationsForReview(
            @Nullable SellerApplicationStatus status, int page, int size) {
        @Nullable BooleanExpression condition =
                status == null ? null : sellerApplication.status.stringValue().eq(status.name());
        List<SellerApplicationReviewView> items =
                query.select(
                                Projections.constructor(
                                        SellerApplicationReviewView.class,
                                        sellerApplication.id,
                                        sellerApplication.memberId,
                                        member.email,
                                        sellerApplication.storeName,
                                        sellerApplication.businessNumber,
                                        sellerApplication.representativeName,
                                        sellerApplication.settlementAccount.bankName,
                                        sellerApplication.settlementAccount.accountHolder,
                                        sellerApplication.settlementAccount.accountNumberMasked,
                                        sellerApplication.status.stringValue(),
                                        sellerApplication.rejectReason,
                                        sellerApplication.submittedAt,
                                        sellerApplication.reviewedAt))
                        .from(sellerApplication)
                        .join(member)
                        .on(member.id.eq(sellerApplication.memberId))
                        .where(condition)
                        .orderBy(sellerApplication.submittedAt.asc(), sellerApplication.id.asc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        Long total =
                query.select(sellerApplication.count())
                        .from(sellerApplication)
                        .where(condition)
                        .fetchOne();
        return new PageView<>(items, Objects.requireNonNullElse(total, 0L), page, size);
    }

    /** 관리자 판매자 목록. 최근 승인 순. 마스킹 전 값이다. */
    public PageView<SellerReviewView> sellers(@Nullable SellerStatus status, int page, int size) {
        @Nullable BooleanExpression condition =
                status == null ? null : seller.status.stringValue().eq(status.name());
        List<SellerReviewView> items =
                query.select(
                                Projections.constructor(
                                        SellerReviewView.class,
                                        seller.id,
                                        seller.storeName,
                                        seller.businessNumber,
                                        seller.representativeName,
                                        seller.status.stringValue(),
                                        seller.suspendReason,
                                        seller.approvedAt))
                        .from(seller)
                        .where(condition)
                        .orderBy(seller.approvedAt.desc(), seller.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        Long total = query.select(seller.count()).from(seller).where(condition).fetchOne();
        return new PageView<>(items, Objects.requireNonNullElse(total, 0L), page, size);
    }

    public Optional<SellerView> seller(UUID sellerId) {
        return Optional.ofNullable(
                query.select(
                                Projections.constructor(
                                        SellerView.class,
                                        seller.id,
                                        seller.storeName,
                                        seller.businessNumber,
                                        seller.representativeName,
                                        seller.settlementAccount.bankName,
                                        seller.settlementAccount.accountHolder,
                                        seller.settlementAccount.accountNumberMasked,
                                        seller.status.stringValue(),
                                        seller.suspendReason,
                                        seller.approvedAt))
                        .from(seller)
                        .where(seller.id.eq(sellerId))
                        .fetchOne());
    }
}
