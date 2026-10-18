---
name: milestone
description: 오픈 마켓 로드맵의 마일스톤(M0~M6)을 시작하거나, 진행 상황을 정리하거나, 마감할 때 사용한다. roadmap.md 갱신, 리스크 확인, E2E 완료 기준, design-review, 태그까지 다룬다.
---

# 마일스톤 진행

원본: [roadmap.md](../../../docs/roadmap.md). 일정 기한은 두지 않는다. 일정 경고도 하지 않는다.

## 시작
1. roadmap.md에서 대상 마일스톤의 체크 항목, 관련 요구사항 ID, 완료 기준 E2E를 읽는다.
2. [risks.md](../../../docs/architecture/risks.md)에서 이 마일스톤에 걸린 리스크를 찾아 가장 먼저 확인할 순서를 정한다.
3. 작업을 PR 단위로 나눈 계획을 사용자에게 보여주고 확인받는다. PR 하나는 리뷰할 수 있는 크기로 한다.
4. roadmap.md 진행 기록 표에 시작일을 적는다.
5. 완료 기준 E2E 시나리오를 `clients/e2e/tests/m<n>-*.spec.ts`(Playwright)로 만들 계획을 마지막 PR로 넣는다. 화면이 없는 확인(API 권한, 이벤트)도 같은 시나리오에서 HTTP로 한다.

## 진행
- PR마다 `main`에서 `<type>/<짧은-설명>` 브랜치를 만든다. push와 PR 생성은 사용자 확인 후 한다.
- 체크 항목을 끝내면 roadmap.md에서 체크한다. 리스크를 확인하면 risks.md 상태와 날짜를 갱신하고, 결정이 생기면 ADR을 쓴다.
- "M<n>에서 확정"이라고 적힌 문서 항목을 확정하면 그 자리를 고친다(CLAUDE.md의 명령 절 등).

## 마감
1. 완료 기준 E2E를 `infra/e2e/run.sh m<n>`으로 실행하고 결과(명령, 출력 요약)를 사용자에게 보여준다. 빈 볼륨의 별도 Compose 프로젝트에서 돌므로 개발용 스택과 앱은 먼저 멈춘다([dev-setup.md](../../../docs/dev-setup.md) "E2E").
2. CI가 녹색인지 확인한다.
3. `design-review` skill로 점검하고, 나온 문제를 고친다.
4. roadmap.md 진행 기록에 완료일과 짧은 회고(잘된 점, 바꿀 점, 다음 마일스톤에 넘기는 것)를 적는다.
5. 사용자가 확인하면 `m<n>` 태그를 달고 push한다.
