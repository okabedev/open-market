// 이벤트 발행·소비 공통: outbox 기록, 멱등 소비(processed_event), EventEnvelope, 재시도·DLT 오류 처리기, 정리 작업(ShedLock).
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api(project(":libs:common-core"))
    // 서비스 리스너가 @KafkaListener와 ConsumerRecord를 쓴다. 오류 처리기는 Boot가 만드는 리스너 팩토리에 붙는다.
    api("org.springframework.boot:spring-boot-starter-kafka")
    // 정리 작업 외에 서비스 스케줄러도 같은 락 실행기를 쓴다.
    api(libs.shedlock.core)

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("tools.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-tracing")
    // 메시징 지표(MessagingMetrics, IgnoredEvents). 서비스의 Prometheus 레지스트리에 센다.
    implementation("io.micrometer:micrometer-core")
    implementation(libs.shedlock.jdbc)
    // DLT 메시지 관리자 API(DeadLetterAdminController). MVC 서비스에만 켜지므로 웹을 강제하지 않는다.
    compileOnly("org.springframework:spring-web")

    testImplementation(project(":libs:common-test"))
    // 관리자 API 테스트: ProblemDetail 변환과 감사 로그 저장
    testImplementation(project(":libs:common-web"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
