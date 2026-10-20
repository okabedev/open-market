package com.openmarket.search.command.domain;

/**
 * 저장소에서 읽은 문서의 버전. 저장할 때 이 버전이 그대로인지 본다(낙관적 동시성). 값의 뜻은 저장소 구현이 정한다.
 *
 * @param sequence 문서를 바꿀 때마다 오르는 번호
 * @param term 번호를 매긴 주체의 세대
 */
public record DocumentVersion(long sequence, long term) {}
