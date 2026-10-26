package com.openmarket.member.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.common.core.privacy.Masking;
import com.openmarket.member.query.dao.SellerQueryDao;
import com.openmarket.member.query.dto.MySellerApplicationView;
import com.openmarket.member.query.dto.PageView;
import com.openmarket.member.query.dto.SellerApplicationReviewView;
import com.openmarket.member.query.dto.SellerApplicationStatus;
import com.openmarket.member.query.dto.SellerReviewView;
import com.openmarket.member.query.dto.SellerStatus;
import com.openmarket.member.query.dto.SellerView;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 입점 신청과 판매자 조회. 관리자에게 주는 값은 여기서 마스킹한다(NFR-7). */
@Service
@Transactional(readOnly = true)
public class SellerQueryService {

    private final SellerQueryDao dao;

    public SellerQueryService(SellerQueryDao dao) {
        this.dao = dao;
    }

    public List<MySellerApplicationView> myApplications(UUID memberId) {
        return dao.myApplications(memberId);
    }

    public SellerView seller(UUID sellerId) {
        return dao.seller(sellerId)
                .orElseThrow(
                        () -> new NotFoundException("MEMBER_SELLER_NOT_FOUND", "판매자를 찾을 수 없습니다."));
    }

    /** 관리자에게 주는 판매자 한 명(정산 지급 화면). 대표자·예금주를 마스킹한다. */
    public SellerView sellerForAdmin(UUID sellerId) {
        SellerView view = seller(sellerId);
        return new SellerView(
                view.sellerId(),
                view.storeName(),
                view.businessNumber(),
                Masking.name(view.representativeName()),
                view.bankName(),
                Masking.name(view.accountHolder()),
                view.accountNumberMasked(),
                view.status(),
                view.suspendReason(),
                view.approvedAt());
    }

    public PageView<SellerApplicationReviewView> applicationsForReview(
            @Nullable SellerApplicationStatus status, int page, int size) {
        PageView<SellerApplicationReviewView> found = dao.applicationsForReview(status, page, size);
        return new PageView<>(
                found.items().stream().map(SellerQueryService::masked).toList(),
                found.totalCount(),
                page,
                size);
    }

    public PageView<SellerReviewView> sellers(@Nullable SellerStatus status, int page, int size) {
        PageView<SellerReviewView> found = dao.sellers(status, page, size);
        return new PageView<>(
                found.items().stream().map(SellerQueryService::masked).toList(),
                found.totalCount(),
                page,
                size);
    }

    private static SellerApplicationReviewView masked(SellerApplicationReviewView view) {
        return new SellerApplicationReviewView(
                view.applicationId(),
                view.memberId(),
                Masking.email(view.applicantEmail()),
                view.storeName(),
                view.businessNumber(),
                Masking.name(view.representativeName()),
                view.bankName(),
                Masking.name(view.accountHolder()),
                view.accountNumberMasked(),
                view.status(),
                view.rejectReason(),
                view.submittedAt(),
                view.reviewedAt());
    }

    private static SellerReviewView masked(SellerReviewView view) {
        return new SellerReviewView(
                view.sellerId(),
                view.storeName(),
                view.businessNumber(),
                Masking.name(view.representativeName()),
                view.status(),
                view.suspendReason(),
                view.approvedAt());
    }
}
