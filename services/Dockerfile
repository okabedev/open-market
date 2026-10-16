# syntax=docker/dockerfile:1
# Spring 서비스 공통 이미지. 빌드 컨텍스트는 services/이고, MODULE에 Gradle 모듈 경로(예: infra/discovery)를 넘긴다.
# compose 파일이 서비스마다 MODULE을 지정한다. 이미지 태그는 ADR 0009 표와 맞춘다.

FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /src
COPY . .
ARG MODULE
# Gradle 배포판과 의존성은 BuildKit 캐시에 둬서 다시 빌드할 때 받지 않는다. 테스트는 이미지 빌드에서 돌리지 않는다.
# 여러 이미지를 동시에 빌드하면 캐시 잠금이 부딪히므로 차례로 쓴다(sharing=locked).
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --quiet ":$(echo "${MODULE}" | tr / :):bootJar" \
 && cp "$(find "${MODULE}/build/libs" -name '*.jar' ! -name '*-plain.jar')" /app.jar \
 && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble
# compose healthcheck가 curl로 /actuator/health를 확인한다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# 자주 바뀌지 않는 층부터 복사해 이미지 층 캐시를 살린다.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
