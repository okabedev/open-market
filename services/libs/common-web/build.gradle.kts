// Spring MVC 서비스 공통: 오류 응답(ProblemDetail), Idempotency-Key 처리와 정리 작업, 요청 로깅, 감사 로그(audit_log).
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api(project(":libs:common-core"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // 멱등성 키 정리 작업. 락 실행기 빈은 common-messaging이 등록한다.
    implementation(libs.shedlock.core)
    // 감사 로그에 trace id를 남긴다.
    implementation("io.micrometer:micrometer-tracing")

    testImplementation(project(":libs:common-test"))
    testImplementation(project(":libs:common-messaging"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
