package com.openmarket.settlement.ui.web;

/**
 * 정산 작업 즉시 실행 결과.
 *
 * @param confirmedCount 이번 실행에서 확정한 정산 수. 다시 돌리면 이미 확정한 판매자·기간은 넘겨 0이다
 */
public record RunSettlementResponse(int confirmedCount) {}
