#!/usr/bin/env bash
# PostToolUse 훅(Bash 도구 전용): implementation 스킬이 --tests로 대상 클래스만 돌릴 때마다
# 실제 판정(카운트/한계 확인/기록)은 failure_gate.py에 맡기고, 그 스크립트의 exit code를
# 그대로 이 훅의 exit code로 넘긴다 — 한계 안에서 실패했으면(막힘) exit 2로 Claude를 멈춰
# 세우고, 한계를 넘었거나(넘김) 통과했으면 exit 0으로 조용히 끝난다.
# acceptance-test는 대상이 아니다 — 거기선 레드가 정상(TDD)이라 실패를 세면 오탐이 난다.
set -u

# 0) 이 훅은 모든 도구 호출마다 불린다(matcher가 Bash로 걸려 있어도 입력은 계속 stdin으로 온다).
#    Bash가 아니거나, 이 스킬이 관심 있는 명령(gradlew ... --tests ...)이 아니면 즉시 조용히 종료한다.
INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
if [ "$TOOL" != "Bash" ]; then exit 0; fi

CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
case "$CMD" in
  *"./gradlew"*"--tests"*) ;;
  *) exit 0 ;;
esac

# 1) implementation 스킬이 실행 중일 때만 관여한다. acceptance-test는 레드가 정상이라 대상이 아니다.
if [ ! -f .claude/gate/state.json ]; then exit 0; fi
SKILL=$(jq -r '.current_skill // empty' .claude/gate/state.json 2>/dev/null)
if [ "$SKILL" != "implementation" ]; then exit 0; fi
TICKET=$(jq -r '.ticket // "T-?"' .claude/gate/state.json 2>/dev/null)

# 2) 방금 실행된 명령어에서 대상 테스트 클래스 이름(FQCN)을 뽑는다.
#    --tests "com.foo.Bar" / --tests 'com.foo.Bar' 두 인용부호 형태를 모두 시도한다.
FQCN=$(echo "$CMD" | sed -nE 's/.*--tests[[:space:]]+"([^"]+)".*/\1/p')
if [ -z "$FQCN" ]; then
  FQCN=$(echo "$CMD" | sed -nE "s/.*--tests[[:space:]]+'([^']+)'.*/\1/p")
fi
if [ -z "$FQCN" ]; then exit 0; fi

# 3) Gradle이 그 클래스 실행 결과로 남긴 JUnit XML을 찾는다. JUnit 5는 @Nested 클래스가
#    있으면 outer 클래스 이름의 XML(TEST-<FQCN>.xml)엔 testcase를 안 남기고, 중첩
#    클래스마다 TEST-<FQCN>$<Nested>.xml로 쪼개 남긴다(이 저장소 인수 테스트는 전부
#    @Nested를 쓴다 — test-guide.md). 그래서 <FQCN>로 시작하는 XML을 전부 모은다.
#    하나도 없으면(빌드/컴파일 실패 등으로 리포트가 아예 안 만들어진 경우) 판단할
#    근거가 없으므로 조용히 넘어간다.
shopt -s nullglob
XMLS=(build/test-results/test/"TEST-${FQCN}".xml build/test-results/test/"TEST-${FQCN}"\$*.xml)
shopt -u nullglob
if [ ${#XMLS[@]} -eq 0 ]; then exit 0; fi

# 4) 누적 카운터 파일을 준비한다 — implementation 스킬 0단계에서 이미 초기화해뒀을 것이므로
#    여기선 파일이 없을 때(비정상적으로 스킵된 경우)만 새로 만든다.
mkdir -p .claude/gate
FAILURES_FILE=.claude/gate/failures.json
[ -f "$FAILURES_FILE" ] || echo '{"by_name":{},"total":0}' > "$FAILURES_FILE"

# 5) 판정은 failure_gate.py가 전담한다: 카운트 증가, 한계(이름별 3 / 총 5) 확인,
#    docs/rotations.md 기록(막힘/넘김/통과), 상태 파일 리셋 여부까지 전부 그 스크립트
#    안에서 처리하고 exit code로 결과를 알려준다 — 이 훅은 그 exit code를 그대로 넘긴다.
python3 "$(dirname "$0")/failure_gate.py" "$FAILURES_FILE" "$TICKET" "${XMLS[@]}"
exit $?
