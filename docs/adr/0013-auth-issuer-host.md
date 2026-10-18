# 0013. auth issuer는 고정 이름 하나, 컨테이너는 host-gateway로 닿는다
- 상태: 승인
- 날짜: 2026-10-18

## 맥락
토큰의 `iss`와 discovery 주소는 하나여야 한다. 그런데 auth에 닿는 쪽은 세 군데다(R7, [auth.md](../architecture/auth.md)).
- 브라우저(Windows): 로그인 화면과 리다이렉트
- IDE·`bootRun`으로 띄운 서비스와 Next.js 서버(WSL): JWK 조회, 토큰 요청
- 컨테이너(gateway, `apps` profile 서비스): JWK 조회, `client_credentials` 토큰 요청

auth 자신도 컨테이너로 뜨거나(기본 profile) 개발할 때 IDE로 뜬다. 처음 계획은 auth 컨테이너에 네트워크 별칭 `auth.openmarket.local`을 주는 것이었다. 실측(2026-10-18)에서 auth를 IDE로 띄우면 별칭이 사라져 컨테이너 gateway가 JWK를 받지 못했다(`Could not obtain the keys`).

## 결정
- issuer는 `http://auth.openmarket.local:9000`으로 고정한다. auth는 요청 주소와 상관없이 이 값을 `iss`와 discovery 문서에 쓴다(`AuthorizationServerSettings.issuer`).
- 누가 auth를 띄우든 auth는 **호스트 포트 9000**에 있다. 컨테이너면 포트를 열고, IDE면 WSL에서 9000으로 듣는다.
- 이름 해석
  - 브라우저·IDE: hosts 파일의 `127.0.0.1 auth.openmarket.local`([dev-setup.md](../dev-setup.md) 3절).
  - 컨테이너: `extra_hosts: auth.openmarket.local:host-gateway`. gateway와 `apps.yaml`의 공통 블록(`x-spring-service`)에 둔다. auth 컨테이너에는 별칭을 주지 않는다.
- 리소스 서버는 `jwk-set-uri`로 키를 받고, `issuer-uri`는 `iss` 값 비교에만 쓴다.

## 결과
- 좋아지는 점
  - auth를 컨테이너와 IDE 사이에서 바꿔도 다른 컨테이너 설정을 고치지 않는다. `docker compose stop auth` 후 bootRun하면 된다.
  - 실측: 컨테이너 auth와 IDE auth 두 경우 모두, 컨테이너 gateway가 같은 이름으로 JWK를 받아 토큰을 검증했다. 서명이 틀리거나 토큰이 없으면 401이었다.
- 감수하는 점
  - 컨테이너끼리 통신이 호스트 포트를 한 번 거친다. 로컬 개발에서는 지연 차이가 없다.
  - Docker Desktop은 `host-gateway`를 IPv6 주소(`fdc4:…::254`)로 풀었다. 리눅스 Docker 엔진에서는 `docker0` 주소가 된다. 둘 다 호스트에 연 포트에 닿는다.
  - WebFlux gateway를 IDE로 띄우면 Reactor Netty DNS 리졸버가 이름을 푼다. 이 리졸버는 `/etc/hosts`는 읽지만 JVM 옵션 `jdk.net.hosts.file`은 무시한다. 그래서 WSL `/etc/hosts`에 실제로 항목이 있어야 한다.
- 후속 작업: Next.js 서버(BFF)를 컨테이너로 띄우는 E2E 구성에도 같은 `extra_hosts`를 둔다.

## 검토한 대안
- **auth 컨테이너에 네트워크 별칭**: 컨테이너 auth에서는 동작하지만, auth를 IDE로 띄우면 컨테이너 쪽 이름이 사라진다. 그때마다 소비자 설정을 바꿔야 한다.
- **컨테이너는 `http://auth:9000`처럼 내부 이름 쓰기**: JWK 조회는 되지만 BFF의 OIDC discovery는 issuer와 같은 주소를 요구한다. 브라우저 주소와 서버 주소가 갈라져 R7이 다시 생긴다.
- **issuer를 `http://localhost:9000`으로**: 컨테이너 안의 localhost는 자기 자신이라 닿지 않는다.
