// search: 상품 이벤트로 만든 Elasticsearch 읽기 모델과 구매자 검색(docs/architecture/search.md, ADR 0016).
plugins {
    id("openmarket.spring-service-conventions")
}

dependencies {
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-messaging"))
    implementation(project(":libs:common-observability"))
    implementation(project(":libs:common-security"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 판매량 테이블(product_sales)과 공통 테이블(멱등 기록 등)을 JDBC로 쓴다. 검색 상품은 Elasticsearch에 있다.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation(libs.springdoc.webmvc.ui)
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.awaitility:awaitility")
}
