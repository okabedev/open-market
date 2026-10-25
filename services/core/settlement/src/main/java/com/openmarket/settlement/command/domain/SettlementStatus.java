package com.openmarket.settlement.command.domain;

/** 정산 상태(docs/architecture/settlement.md "정산 확정"). */
public enum SettlementStatus {
    /** 배치가 확정했다. 지급을 기다린다. */
    CONFIRMED,
    /** 관리자가 지급 완료로 기록했다. 끝 상태. */
    PAID_OUT
}
