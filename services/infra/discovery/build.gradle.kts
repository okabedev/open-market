// discovery: Eureka 서버. 도메인 로직이 없어 DDD 패키지 규약과 ArchUnit 규칙을 적용하지 않는다.
// config 서버에서 설정을 받지 않는다. 다른 서비스보다 먼저 떠야 하므로 설정은 jar 안에 둔다.
plugins {
    id("openmarket.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
