#!/usr/bin/env bash
# PostToolUse 훅(Bash 도구 전용): implementation 스킬이 --tests로 대상 클래스만 돌릴 때마다
# 방금 나온 JUnit XML을 읽어 같은 테스트가 3번째로 같은 이유로 실패하거나 이번 구현 시도에서
# 총 5번째 실패하면 exit 2로 개입한다("맴돌다 끊김" 자기판단이 안 될 경우의 안전망).
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

# 2) 방금 실행된 명령어에서 대상 테스트 클래스 이름(FQCN)을 뽑는다.
#    --tests "com.foo.Bar" / --tests 'com.foo.Bar' 두 인용부호 형태를 모두 시도한다.
FQCN=$(echo "$CMD" | sed -nE 's/.*--tests[[:space:]]+"([^"]+)".*/\1/p')
if [ -z "$FQCN" ]; then
  FQCN=$(echo "$CMD" | sed -nE "s/.*--tests[[:space:]]+'([^']+)'.*/\1/p")
fi
if [ -z "$FQCN" ]; then exit 0; fi

# 3) Gradle이 그 클래스 실행 결과로 남긴 JUnit XML을 찾는다. 없으면(빌드/컴파일 실패 등으로
#    아예 리포트가 안 만들어진 경우) 이 훅은 판단할 근거가 없으므로 조용히 넘어간다.
XML="build/test-results/test/TEST-${FQCN}.xml"
if [ ! -f "$XML" ]; then exit 0; fi

# 4) 누적 카운터 파일을 준비한다 — implementation 스킬 0단계에서 이미 초기화해뒀을 것이므로
#    여기선 파일이 없을 때(비정상적으로 스킵된 경우)만 새로 만든다.
mkdir -p .claude/gate
FAILURES_FILE=.claude/gate/failures.json
[ -f "$FAILURES_FILE" ] || echo '{"by_name":{},"total":0}' > "$FAILURES_FILE"

# 5) 실제 카운트 증가와 한계(이름별 3 / 총 5) 판정은 count_failures.py가 한다.
#    실패가 없었으면 "OK"(카운터 리셋), 한계를 안 넘었으면 "OK"(카운터만 갱신),
#    넘었으면 "HANDOFF:<이유>"를 표준출력으로 돌려준다.
RESULT=$(python3 "$(dirname "$0")/count_failures.py" "$XML" "$FAILURES_FILE")

# 6) HANDOFF면 exit 2로 개입해 Claude가 이 시도를 이어가기 전에 반드시 이 메시지를 보게 만든다.
#    한계를 안 넘었으면(RESULT가 "OK") 아무 출력 없이 exit 0으로 조용히 끝난다.
if [ "${RESULT#HANDOFF:}" != "$RESULT" ]; then
  REASON="${RESULT#HANDOFF:}"
  echo "[안전망] $REASON — 더 시도하지 말고 지금 멈추세요. 자기판단 없이도 강제 발동한 안전망입니다: '맴돌다 끊김'으로 판단해 docs/rotations.md에 한 줄 남기고, 지금까지 상태를 사용자에게 보고한 뒤 다음을 사용자가 정하게 하세요." >&2
  exit 2
fi
exit 0
