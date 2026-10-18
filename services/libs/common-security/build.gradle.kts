// 서비스 리소스 서버 공통: 사용자·서비스 간 토큰 검증, 역할 경로 규칙, roles·scope 권한 변환(자동설정, docs/architecture/auth.md "인가" 2).
// 외부 API나 내부 API를 받는 서비스가 의존한다.
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
