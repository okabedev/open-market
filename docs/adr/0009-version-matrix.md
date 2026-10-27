# 0009. 버전 조합
- 상태: 승인
- 날짜: 2026-10-16

## 맥락
Java 25, Spring Boot 4, Spring Cloud, QueryDSL을 함께 써야 한다([risks.md](../architecture/risks.md) R1~R3). 버전을 문서에 흩어 적으면 서로 어긋나므로, 직접 고정하는 버전의 근거를 이 ADR 한 곳에 둔다. 실제 값의 원본은 빌드 파일(`services/gradle/libs.versions.toml`, Gradle 래퍼)이다. 버전을 올리면 빌드 파일과 이 문서의 표를 같이 고친다.

## 결정
**원칙**: Spring Boot와 Spring Cloud BOM이 관리하는 라이브러리는 버전을 직접 적지 않는다. BOM 밖의 라이브러리만 카탈로그에 고정한다.

### 플랫폼
| 항목 | 버전 | 근거 (2026-10-16 확인) |
|---|---|---|
| JDK | 25 (Temurin 25.0.4.1 LTS) | LTS. start.spring.io가 Java 25를 지원 목록에 올린다 |
| Gradle | 9.8.1 | Gradle 호환표: Java 25는 9.1.0부터 지원 |
| Spring Boot | 4.1.1 | 최신 GA. 4.1.x OSS 지원은 2027-07-31까지(4.0.x는 2026-12-31 종료, 4.2는 아직 마일스톤) |
| Spring Cloud | 2025.1.3 | start.spring.io 호환 범위: Boot `>=4.0.0, <4.2.0-M1` |

### Boot BOM이 관리하는 주요 버전 (참고, 직접 고정하지 않음)
Spring Framework 7.0.9, Spring Security 7.1.1, Spring Data 2026.0.1, Hibernate 7.4.5, Jakarta Persistence 3.2.0, Jackson 3.1.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Kafka client 4.2.1, Spring Kafka 4.1.1, Lettuce 7.5.2, Elasticsearch client 9.4.5, Micrometer Tracing 1.7.1, OpenTelemetry 1.62.0, Testcontainers 2.0.5, JUnit Jupiter 6.0.3, AssertJ 3.27.7, JSpecify 1.0.1.

Spring Authorization Server는 Spring Security 7.x에 포함되어 Boot BOM이 관리한다.

### 직접 고정하는 라이브러리
| 항목 | 버전 | 용도·비고 |
|---|---|---|
| QueryDSL (OpenFeign 포크, `io.github.openfeign.querydsl`) | 7.7 | 7.0부터 JPA 3.2·Hibernate 7 지원. JDK 23+에서는 annotation processor 경로를 명시해야 Q-타입이 생성된다(7.6 문서). Gradle `annotationProcessor` 구성으로 명시한다 |
| springdoc-openapi | 3.1.1 | Boot 4용 3.x 라인. 3.1.0에서 Boot 4.1.0 기준으로 올라감 |
| ArchUnit | 1.5.1 | 패키지 규약 검사 |
| ShedLock | 7.10.1 | 스케줄 작업 단일 실행. `shedlock-core`와 `shedlock-provider-jdbc-template`만 쓴다(애노테이션 AOP 대신 `LockingTaskExecutor`를 직접 호출) |
| TwelveMonkeys ImageIO (`imageio-jpeg`, `imageio-webp`) | 3.15.2 | file의 이미지 읽기(JPEG CMYK·EXIF, WebP). 2026-10-06 릴리스된 3.15.3은 2주 지난 뒤 검토한다. [ADR 0017](0017-image-processing.md). 2026-10-19 추가 |
| uuid-creator | 6.1.1 | UUID v7 생성(`common-core`). JDK 표준에는 v7 생성기가 없다 |
| opentelemetry-logback-appender-1.0 | 2.28.1-alpha | Logback 로그를 OTLP로 보낸다(`common-observability`). Boot에 포함되지 않는다. Boot BOM의 OpenTelemetry 1.62.0으로 빌드된 마지막 버전이다(2.29.0부터 1.63.0 이상). Boot의 OpenTelemetry를 올릴 때 함께 올린다. 2026-10-18 추가 |

### 코드 스타일 도구 후보 (선택과 빌드 확인 결과는 [ADR 0010](0010-code-style-tools.md))
| 항목 | 버전 | Java 25 근거 |
|---|---|---|
| Spotless Gradle 플러그인 | 8.10.4 | 포매터 실행기 |
| google-java-format | 1.36.1 (선택) | 1.34.0부터 Java 25 문법 지원. 1.37.0은 Spotless 8.10.4와 호환되지 않음(ADR 0010) |
| palantir-java-format | 2.102.0 (제외) | Java 25 모듈 import를 파싱하지 못함(ADR 0010) |
| Error Prone / Gradle 플러그인 `net.ltgt.errorprone` | 2.50.0 / 5.1.1 | 2.45.0에서 JDK 26 EA 호환 개선 |
| NullAway | 0.14.2 | 0.13.0부터 JDK 25로 CI 빌드 |

### 컨테이너 이미지
| 이미지 | 태그 | 비고 |
|---|---|---|
| postgres | 18.6-alpine | Testcontainers(`common-test` `TestImages`)와 M0-c Compose가 같은 태그를 쓴다 |
| apache/kafka | 4.2.2 | KRaft 단일 노드. Boot BOM의 Kafka 클라이언트(4.2.x)와 같은 라인. Testcontainers(`TestImages`)도 같은 태그를 쓴다 |
| quay.io/debezium/connect | 3.6.2.Final | 2026-10-16 릴리스된 3.6.3·3.7.0은 2주 지난 뒤 검토한다 |
| redis | 8.10.2-alpine | |
| axllent/mailpit | v1.31.6 | |
| kafbat/kafka-ui | v1.5.0 | provectuslabs/kafka-ui는 유지보수가 멈춰 후속 포크를 쓴다 |
| elasticsearch | 9.4.6 | `search` profile. Boot BOM의 Elasticsearch 클라이언트(9.4.x)와 같은 라인. nori 커스텀 이미지(`infra/elasticsearch/Dockerfile`, [ADR 0016](0016-elasticsearch-nori.md))의 기반 태그다 |
| otel/opentelemetry-collector-contrib | 0.161.0 | `obs` profile |
| prom/prometheus | v3.13.4 | `obs` profile |
| grafana/tempo | 3.1.0 | `obs` profile. 3.x 단일 바이너리 모드는 Kafka 없이 동작한다 |
| grafana/loki | 3.7.8 | `obs` profile |
| grafana/grafana | 13.2.3 | `obs` profile |
| node | 24.21.0-alpine3.24 | `e2e` profile의 가짜 PG(`infra/fake-pg`). 런타임 메이저는 `.nvmrc`와 같다. 24.22.0은 2026-10-14 릴리스라 2주가 지나지 않았다 |
| ghcr.io/shopify/toxiproxy | 2.12.0 | `fault` profile의 장애 주입(`infra/fault`, M6). 서비스 → Postgres TCP 프록시로 지연·끊김을 넣는다. 2025-03-18 릴리스. 2.13.0(2026-10-14)은 2주가 지나지 않아 쓰지 않았다. 2026-10-27 추가 |
| grafana/k6 | 2.3.0 | `load` profile의 부하 시험(`infra/k6`, M6). 2026-09-21 릴리스. 2.0에서 바뀐 것은 클라우드 명령과 실험 모듈 제거라 HTTP 시나리오에는 영향이 없다. 2026-10-26 추가 |
| eclipse-temurin | 25.0.4.1_1-jdk-noble, 25.0.4.1_1-jre-noble | `services/Dockerfile`의 빌드·실행 단계. 실행 이미지에는 healthcheck용 curl만 더한다 |

Compose 이미지 태그의 원본은 `infra/compose`의 compose 파일이고, Spring 서비스 기반 이미지는 `services/Dockerfile`이다. 태그를 올리면 그 파일과 이 표를 같이 고친다.

이후 마일스톤에서 쓸 컨테이너 이미지와 라이브러리(Resilience4j 설정, 프론트 패키지 등)는 해당 마일스톤에서 이 표에 추가한다.

## 결과
- 좋아지는 점: 지원 기간이 가장 긴 GA 조합이다. 대부분의 버전을 BOM에 맡겨 충돌이 적다.
- 감수하는 점: Boot 4.1.x OSS 지원이 끝나기 전에(2027-07) 4.2 이상으로 올려야 한다. QueryDSL 포크는 커뮤니티 유지보수에 기댄다.
- 확인 상태: R1은 메타데이터로, R2(Q-타입 생성)와 R3(도구 동작)는 `services/build-logic`의 `ConventionPluginsTest` 빌드로 확인했다.

## 검토한 대안
- Spring Boot 4.0.x: OSS 지원이 2026-12-31에 끝난다.
- Spring Boot 4.2: 아직 마일스톤이고 Spring Cloud 2026.0도 마일스톤이다.
- 원본 QueryDSL(`com.querydsl`) 5.x: Jakarta와 Hibernate 7 대응이 늦고 릴리스가 멈춰 있다.
- Spring Data JPA Specification/Criteria만 사용: 의존성은 줄지만 프로젝션 조회 코드가 길어지고 타입 안정성이 떨어진다.
