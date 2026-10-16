plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.plugin.spring.boot)
    implementation(libs.gradle.plugin.spotless)
    implementation(libs.gradle.plugin.errorprone)

    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // 픽스처 빌드가 이 build-logic과 저장소의 버전 카탈로그를 쓰도록 경로를 넘긴다.
    systemProperty("buildLogicDir", projectDir.absolutePath)
    systemProperty("catalogPath", projectDir.resolve("../gradle/libs.versions.toml").absolutePath)
    inputs.dir("src/main/kotlin")
    inputs.file("../gradle/libs.versions.toml")
}
