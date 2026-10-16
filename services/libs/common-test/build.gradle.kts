// 서비스 테스트 공통: ArchUnit 규칙, Testcontainers 설정. 서비스는 testImplementation으로만 쓴다.
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-test")
    api("org.springframework.boot:spring-boot-testcontainers")
    api("org.testcontainers:testcontainers-junit-jupiter")
    api("org.testcontainers:testcontainers-postgresql")
    api(libs.archunit.junit5)

    // 규칙 픽스처의 Q-타입 예시용
    testImplementation(libs.querydsl.jpa)
}
