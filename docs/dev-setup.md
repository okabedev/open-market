# 개발 환경 준비

기준 환경은 Windows + WSL2(Ubuntu)다. macOS나 Linux도 같은 도구를 쓰면 된다.
빌드·실행 명령은 M0-b, M0-c에서 확정되면 이 문서에 추가한다.

## 1. 도구
| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 25 (Temurin) | `JAVA_HOME` 설정. Gradle은 래퍼로 실행하므로 따로 설치하지 않는다 |
| Node.js | 24 LTS | 레포 루트 `.nvmrc`. nvm 사용 시 `nvm use` |
| pnpm | `clients/package.json`의 `packageManager` 값 (M0-e에서 고정) | `corepack enable`로 맞춘다 |
| Docker | Docker Desktop(WSL 연동) 또는 WSL 안의 Docker Engine | Compose v2 |
| gh | 최신 | GitHub 작업용 |

## 2. WSL 메모리
전체 스택은 메모리를 많이 쓴다([risks.md](architecture/risks.md) R6). Windows 사용자 폴더의 `.wslconfig`에 할당량을 적는다. 권장값은 R6 실측 후 여기에 적는다.

```ini
[wsl2]
memory=16GB
processors=6
```

## 3. hosts
auth의 issuer 주소를 브라우저와 컨테이너가 같게 보도록 이름을 등록한다([auth.md](architecture/auth.md)).
브라우저가 Windows에서 돌면 **Windows hosts 파일**(`C:\Windows\System32\drivers\etc\hosts`)에 추가한다. WSL 안에서 서비스를 IDE로 실행하면 WSL의 `/etc/hosts`에도 추가한다.

```
127.0.0.1 auth.openmarket.local
```

## 4. 환경 변수
```bash
cp .env.example .env
```
- 토스페이먼츠 테스트 키는 [토스페이먼츠 개발자센터](https://developers.tosspayments.com)에서 발급받아 `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`에 넣는다. `test_`로 시작하는 키만 쓴다.
- `.env`는 커밋하지 않는다.

## 5. 실행
M0-c에서 확정한다. 예정된 구성은 [overview.md](architecture/overview.md)의 "로컬 실행 구성"을 본다.
