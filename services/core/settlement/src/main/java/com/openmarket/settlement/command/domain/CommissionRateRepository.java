package com.openmarket.settlement.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 수수료율 저장소. 구현은 {@code infra}에 있다. */
public interface CommissionRateRepository {

    CommissionRate save(CommissionRate rate);

    /** 요율 변경·삭제. 같은 카테고리를 동시에 바꾸면 한쪽이 기다린다. */
    Optional<CommissionRate> findForUpdateById(UUID categoryId);

    List<CommissionRate> findAllById(Iterable<UUID> categoryIds);

    void delete(CommissionRate rate);
}
