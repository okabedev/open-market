// 실행 가능한 Spring Boot 서비스(core/*, infra/*).
plugins {
    id("openmarket.java-conventions")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(platform(libs.findLibrary("spring-cloud-dependencies").get()))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

springBoot {
    buildInfo()
}

// 앱 통합 테스트가 OpenAPI 문서를 프론트 api-client의 파일과 비교한다(common-test OpenApiSnapshot).
// 그 파일은 Gradle 프로젝트 밖에 있으므로 테스트 입력으로 적어, 파일만 바뀌어도 테스트가 다시 돈다.
// 갱신 모드(OPENAPI_UPDATE)도 입력으로 둬서 캐시된 결과를 쓰지 않게 한다.
val openApiSnapshots = layout.settingsDirectory.dir("../clients/packages/api-client/openapi")
tasks.named<Test>("test") {
    inputs.files(fileTree(openApiSnapshots) { include("*.json") })
        .withPropertyName("openApiSnapshots")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("openApiUpdate", providers.environmentVariable("OPENAPI_UPDATE").orElse(""))
}

// bootRun은 레포 루트 .env를 환경 변수로 넣는다. config-repo의 ${MEMBER_DB_PASSWORD} 같은 자리 표시자를 푼다.
// 셸에 이미 있는 변수가 이긴다. IDE로 실행할 때는 IDE 실행 설정에 .env를 지정한다(docs/dev-setup.md).
val envFile = layout.settingsDirectory.file("../.env").asFile
tasks.named<JavaExec>("bootRun") {
    doFirst {
        val run = this as JavaExec
        if (envFile.isFile) {
            envFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .map { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
                .filter { (key, _) -> key !in run.environment }
                .forEach { (key, value) -> run.environment(key, value) }
        }
    }
}
