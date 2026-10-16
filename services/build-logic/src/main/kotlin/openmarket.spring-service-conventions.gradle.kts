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
