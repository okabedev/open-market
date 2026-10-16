// 이벤트 발행·소비 공통: outbox 기록, 멱등 소비(processed_event), EventEnvelope, 정리 작업(ShedLock).
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api(project(":libs:common-core"))
    // 리스너가 ConsumerRecord를 그대로 넘긴다.
    api("org.apache.kafka:kafka-clients")
    // 정리 작업 외에 서비스 스케줄러도 같은 락 실행기를 쓴다.
    api(libs.shedlock.core)

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("tools.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-tracing")
    implementation(libs.shedlock.jdbc)

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
