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
