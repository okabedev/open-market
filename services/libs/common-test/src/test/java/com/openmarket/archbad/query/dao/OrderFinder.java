package com.openmarket.archbad.query.dao;

import com.openmarket.archbad.command.application.LeakyUseCase;

/** 위반: 이름이 QueryDao로 끝나지 않고, 커맨드 모델을 쓴다. */
public class OrderFinder {
    public void find() {
        new LeakyUseCase().execute();
    }
}
