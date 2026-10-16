// Spring MVC 서비스 공통: 오류 응답(ProblemDetail), Idempotency-Key 처리, 요청 로깅.
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    api(project(":libs:common-core"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
