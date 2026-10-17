// 관측성 공통: OpenTelemetry 스타터(트레이스·로그 OTLP 내보내기)와 Logback 로그를 OpenTelemetry로 넘기는 어펜더(자동설정).
// OpenTelemetry를 쓰는 서비스는 스타터 대신 이 모듈을 의존한다.
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-opentelemetry")
    // Boot는 이 어펜더를 포함하지 않는다. 버전은 Boot BOM의 OpenTelemetry(1.62.0)에 맞춘다(ADR 0009).
    implementation(libs.opentelemetry.logback.appender)
    implementation("ch.qos.logback:logback-classic")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
