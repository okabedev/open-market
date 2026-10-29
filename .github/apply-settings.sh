#!/bin/bash
# GitHub 저장소 설정을 이 디렉터리의 선언과 같게 맞춘다. 다시 실행해도 결과가 같다.
# - merge: squash만 허용한다. 커밋 제목은 PR 제목, 본문은 커밋 메시지(Co-Authored-By가 남는다). merge 뒤 브랜치를 지운다.
# - 라벨: labels.txt와 같게 만들고, 없는 라벨은 지운다.
# - main 보호: rulesets/main.json. PR 필수(승인 0명, 1인 저장소), squash만, `ci` 체크 통과와 최신 main 반영 필수,
#   강제 push·삭제 금지, 우회 없음. `ci`는 .github/workflows/ci.yml의 집계 잡이고 15368은 GitHub Actions 앱 ID다.
# - Dependabot 알림: 켠다. Gradle 의존성은 .github/workflows/dependencies.yml이 그래프를 제출한다(ADR 0026).
#   보안 업데이트 PR은 켜지 않는다. 버전은 사람이 2주 규칙으로 올린다(ADR 0009).
# 저장소 관리자 권한으로 gh에 로그인해야 한다. 실행: .github/apply-settings.sh [owner/repo]
set -euo pipefail

DIR="$(dirname "$0")"
REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"

gh api -X PATCH "repos/$REPO" --silent \
    -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false \
    -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=COMMIT_MESSAGES \
    -F delete_branch_on_merge=true -F allow_update_branch=true
echo "merge 설정 적용"

gh api -X PUT "repos/$REPO/vulnerability-alerts" --silent
echo "Dependabot 알림 적용"

declare -A declared=()
while read -r name color description; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    gh label create "$name" -R "$REPO" --color "$color" --description "$description" --force
    declared[$name]=1
done <"$DIR/labels.txt"
gh label list -R "$REPO" --limit 500 --json name -q '.[].name' | while read -r name; do
    if [[ -z "${declared[$name]:-}" ]]; then
        gh label delete "$name" -R "$REPO" --yes
    fi
done
echo "라벨 ${#declared[@]}개 적용"

id=$(gh api "repos/$REPO/rulesets" -q '.[] | select(.name == "main") | .id')
if [[ -n "$id" ]]; then
    gh api -X PUT "repos/$REPO/rulesets/$id" --input "$DIR/rulesets/main.json" --silent
else
    gh api -X POST "repos/$REPO/rulesets" --input "$DIR/rulesets/main.json" --silent
fi
echo "main ruleset 적용"
