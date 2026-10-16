// QueryDSL 조회를 쓰는 모듈. Q-타입은 build/generated/sources/annotationProcessor에 생성된다.
// JDK 23+는 클래스패스의 annotation processor를 자동으로 돌리지 않으므로 processor 경로를 명시한다(ADR 0009).
plugins {
    id("openmarket.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val querydslVersion = libs.findVersion("querydsl").get().requiredVersion

dependencies {
    implementation(libs.findLibrary("querydsl-jpa").get())
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:$querydslVersion:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}
