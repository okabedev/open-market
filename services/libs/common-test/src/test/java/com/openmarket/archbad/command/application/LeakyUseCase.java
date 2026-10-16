package com.openmarket.archbad.command.application;

import com.openmarket.archbad.infra.LegacyClient;

/** 위반: 유스케이스가 infra를 직접 쓴다. */
public class LeakyUseCase {
    public void execute() {
        new LegacyClient().call();
    }
}
