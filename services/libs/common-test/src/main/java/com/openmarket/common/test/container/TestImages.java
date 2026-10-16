package com.openmarket.common.test.container;

import org.testcontainers.utility.DockerImageName;

/** 테스트 컨테이너 이미지. 로컬 Compose(infra/compose)와 같은 태그를 쓴다. */
public final class TestImages {

    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.6-alpine");

    private TestImages() {}
}
