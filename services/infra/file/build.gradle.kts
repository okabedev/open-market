// file: 업로드, 형식 검사, 썸네일, 로컬 볼륨 저장과 공개 제공, 오래된 파일 정리(docs/architecture/file.md, ADR 0008).
plugins {
    id("openmarket.spring-service-conventions")
    id("openmarket.querydsl-conventions")
}

dependencies {
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-messaging"))
    implementation(project(":libs:common-observability"))
    implementation(project(":libs:common-security"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.twelvemonkeys.imageio.jpeg)
    implementation(libs.twelvemonkeys.imageio.webp)
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(project(":libs:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.awaitility:awaitility")
}

// 파일 저장 위치(config-repo file.yml의 FILE_STORAGE_ROOT). bootRun은 레포의 infra/compose/data/file에 둔다(커밋하지 않는 디렉터리).
tasks.named<JavaExec>("bootRun") {
    environment(
        "FILE_STORAGE_ROOT",
        layout.settingsDirectory.dir("../infra/compose/data/file").asFile.absolutePath,
    )
}
