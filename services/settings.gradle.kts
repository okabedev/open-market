pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "open-market"

// 모듈은 추가할 때마다 여기에 등록한다 (add-service skill).
include(":libs:common-core")
include(":libs:common-web")
include(":libs:common-messaging")
include(":libs:common-test")
