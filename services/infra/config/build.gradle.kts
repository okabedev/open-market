// config: Spring Cloud Config 서버(native 백엔드). 저장소는 레포의 infra/config-repo다.
// 도메인 로직이 없어 DDD 패키지 규약과 ArchUnit 규칙을 적용하지 않는다.
plugins {
    id("openmarket.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
