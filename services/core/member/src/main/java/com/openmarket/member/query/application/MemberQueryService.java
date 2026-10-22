package com.openmarket.member.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.member.query.dao.MemberQueryDao;
import com.openmarket.member.query.dto.AddressView;
import com.openmarket.member.query.dto.MemberContactView;
import com.openmarket.member.query.dto.MemberView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 본인 프로필과 배송지 조회(ACC-3). */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberQueryDao dao;

    public MemberQueryService(MemberQueryDao dao) {
        this.dao = dao;
    }

    /** 가입 직후에는 auth의 가입 이벤트가 아직 오지 않아 404일 수 있다. */
    public MemberView member(UUID memberId) {
        return dao.findMember(memberId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."));
    }

    /** 알림 이메일의 받는 주소(notification). 없거나 탈퇴한 회원은 404 {@code MEMBER_NOT_FOUND}. */
    public MemberContactView contact(UUID memberId) {
        return dao.findContact(memberId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."));
    }

    public List<AddressView> addresses(UUID memberId) {
        return dao.findAddresses(memberId);
    }
}
