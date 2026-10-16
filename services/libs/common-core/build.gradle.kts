// 프레임워크에 의존하지 않는 공통 타입: 예외와 오류 분류, Money, ID 생성, 개인정보 마스킹.
plugins {
    id("openmarket.library-conventions")
}

dependencies {
    implementation(libs.uuid.creator)
}
