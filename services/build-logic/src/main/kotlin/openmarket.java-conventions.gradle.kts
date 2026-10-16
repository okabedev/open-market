// 모든 Java 모듈의 공통 설정: 툴체인, 컴파일 옵션, 포매터, 정적 분석, 테스트.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(version("java"))
    }
}

dependencies {
    // Boot BOM으로 버전을 맞춘다. 서비스가 아닌 라이브러리 모듈도 같은 버전을 쓴다.
    val bom = platform(lib("spring-boot-dependencies"))
    implementation(bom)
    annotationProcessor(bom)
    testImplementation(bom)

    implementation("org.jspecify:jspecify")

    errorprone(lib("errorprone-core"))
    errorprone(lib("nullaway"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation,unchecked"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.openmarket")
        option("NullAway:JSpecifyMode", "true")
        // JPA가 기본 생성자로 만든 뒤 필드를 채우므로, 이 애노테이션이 붙은 클래스의 기본 생성자는 초기화 검사에서 뺀다.
        option(
            "NullAway:ExternalInitAnnotations",
            "jakarta.persistence.Entity,jakarta.persistence.Embeddable,jakarta.persistence.MappedSuperclass",
        )
    }
}

// 테스트 코드는 Error Prone 기본 검사만 하고 NullAway는 끈다.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

spotless {
    java {
        target("src/**/*.java")
        // palantir-java-format 2.102.0은 Java 25 모듈 import를 파싱하지 못해 google-java-format을 쓴다(ADR 0010).
        googleJavaFormat(version("google-java-format")).aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-Dfile.encoding=UTF-8", "-XX:+EnableDynamicAgentLoading")
}
