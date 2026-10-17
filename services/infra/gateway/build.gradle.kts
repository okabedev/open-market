// gateway: 외부 진입점. 라우팅, JWT 검증, 경로별 역할 검사, 레이트 리밋(Redis), 내부 헤더 제거, 요청 ID.
// Spring Cloud Gateway WebFlux 구현을 쓴다(ADR 0011). 도메인 로직이 없어 DDD 패키지 규약과 ArchUnit 규칙을 적용하지 않는다.
plugins {
    id("openmarket.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":libs:common-observability"))
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
}
