# 0010. 코드 스타일과 정적 분석 도구
- 상태: 승인
- 날짜: 2026-10-16

## 맥락
포맷과 null 안전성은 리뷰에서 사람이 지적하지 않고 빌드가 강제해야 한다. Java 25 문법을 처리하지 못하는 도구는 쓸 수 없다([risks.md](../architecture/risks.md) R3). 후보와 버전은 [ADR 0009](0009-version-matrix.md)에 있다.

## 결정
| 영역 | 도구 | 설정 |
|---|---|---|
| 포매터 | Spotless 8.10.4 + google-java-format 1.36.1, AOSP 스타일(4칸 들여쓰기) | `check`에 포함. 고칠 때는 `spotlessApply` |
| 버그 패턴 | Error Prone 2.50.0 | 기본 검사. ERROR 등급은 컴파일 실패 |
| null 안전성 | NullAway 0.14.2, JSpecify 모드 | `com.openmarket` 패키지 전체를 null 아님이 기본으로 본다. null이 될 수 있으면 `org.jspecify.annotations.Nullable`을 붙인다 |

세부 규칙
- NullAway는 생성 코드(`build/generated`, QueryDSL Q-타입)와 테스트 코드에는 적용하지 않는다. 테스트 코드도 Error Prone 기본 검사는 받는다.
- JPA 엔티티는 JPA가 기본 생성자로 만든 뒤 필드를 채운다. 그래서 `@Entity`, `@Embeddable`, `@MappedSuperclass` 클래스의 기본 생성자는 NullAway 초기화 검사에서 뺀다(`ExternalInitAnnotations`). 다른 생성자는 그대로 검사한다.
- 설정은 `services/build-logic`의 `openmarket.java-conventions` 플러그인 한 곳에 둔다. 모듈 빌드 파일에서 검사를 끄지 않는다.
- 도구가 Java 25에서 동작하는지는 `ConventionPluginsTest`(Gradle TestKit)가 회귀 테스트로 확인한다. 픽스처에는 모듈 import, 레코드 패턴, unnamed 변수, `super()` 앞 문장을 넣었다.

## 확인 결과 (2026-10-16)
- palantir-java-format 2.102.0: Java 25 모듈 import(`import module java.base;`)를 파싱하지 못한다(`Expected ; after import`).
- google-java-format 1.37.0: 단독 실행으로는 같은 픽스처를 정상으로 포맷한다. 하지만 Spotless 8.10.4에서 부르면 실패한다. 1.37.0에서 `JavaFormatterOptions.Style`이 레코드로 바뀌어 Spotless의 리플렉션 호출이 깨졌다(diffplug/spotless#3126). 수정은 google-java-format 쪽 커밋 a51e46a로 들어갔지만 아직 릴리스되지 않았다.
- google-java-format 1.36.1: Spotless로 픽스처 전체를 포맷한다. 추가 JVM 인자는 필요 없다.
- Error Prone + NullAway: Java 25 픽스처를 컴파일하고, `@NonNull` 반환 메서드의 `return null`을 컴파일 오류로 잡는다.
- configuration cache: 픽스처 빌드에서 캐시 저장과 재사용 모두 성공했다.

## 결과
- 좋아지는 점: 포맷 논쟁이 없다. NPE로 이어질 코드를 컴파일 단계에서 막는다. Spring Framework 7과 Boot 4가 쓰는 JSpecify 애노테이션과 같은 체계다.
- 감수하는 점: null이 될 수 있는 값마다 `@Nullable`을 붙여야 한다. 컴파일이 조금 느려진다.
- 후속 작업: google-java-format이 a51e46a를 포함한 새 버전을 내면 Spotless와 함께 올리고 `ConventionPluginsTest`로 확인한다.

## 검토한 대안
- palantir-java-format: 람다와 긴 줄 처리가 보기 좋지만 Java 25 문법을 지원하지 않는다.
- google-java-format 기본 스타일(2칸): `.editorconfig`의 Java 4칸 규칙과 맞지 않는다.
- Checkstyle: 포맷은 포매터가 강제하므로 겹치는 검사가 많다. 이름 규칙은 ArchUnit이 맡는다.
- NullAway 없이 Error Prone만: null 계약을 문서와 리뷰에만 맡기게 된다.
