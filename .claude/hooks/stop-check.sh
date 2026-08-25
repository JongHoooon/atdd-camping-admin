#!/usr/bin/env bash
# Stop 훅: 턴이 끝날 때 .claude/gate/state.json을 읽어
#   1) 테스트를 실제로 돌렸는지(tests_verified) — 3개 스킬(acceptance-test/implementation/verdict)만 해당
#   2) 완료 기준에 못 미친 채 멈췄다면(completed=false) docs/rotations.md에 새 기록이 있는지
# 를 확인해 hookSpecificOutput.additionalContext로 알려준다. 절대 종료를 막지 않는다(exit 2 없음).
set -u

# 1) state.json 읽기 — 스킬이 0단계에서 남긴 자기보고를 그대로 가져온다.
#    current_skill이 비어 있으면(스킬 실행 중이 아니거나 이미 리셋됐으면) 아래 블록 전체를 건너뛴다.
if [ -f .claude/gate/state.json ]; then
  SKILL=$(jq -r '.current_skill // empty' .claude/gate/state.json 2>/dev/null)
  COMPLETED=$(jq -r '.completed // false' .claude/gate/state.json 2>/dev/null)
  # tests_verified 필드 자체가 없으면(acceptance-criteria) 테스트 실행 여부는 애초에 안 다룬다.
  HAS_TESTS_FIELD=$(jq -r 'has("tests_verified")' .claude/gate/state.json 2>/dev/null)
  VERIFIED=$(jq -r '.tests_verified // false' .claude/gate/state.json 2>/dev/null)
  BASELINE=$(jq -r '.rotations_baseline // 0' .claude/gate/state.json 2>/dev/null)
else
  SKILL=""
fi

if [ -n "$SKILL" ]; then
  # 2) 테스트 실행 여부 확인 — 통과/실패가 아니라 "돌렸는가"만 본다(TDD 레드 페이즈 오탐 방지).
  TEST_PART=""
  if [ "$HAS_TESTS_FIELD" = "true" ]; then
    if [ "$VERIFIED" = "true" ]; then
      TEST_PART="테스트 실행 기록 확인됨"
    else
      TEST_PART="경고: 테스트 실행 기록 없음 — 실제로 테스트를 돌렸는지 확인하세요"
    fi
  fi

  # 3) 완료 못 하고 멈췄는지, 멈췄다면 rotations.md에 실제로 기록을 남겼는지 확인.
  #    스킬 시작 시점 줄 수(BASELINE)와 지금 줄 수를 비교하는 방식이라, "기록을 남겨야 한다"는
  #    말뿐인 리마인더가 아니라 실제 파일 diff에 근거한 확인/경고를 낸다.
  #    completed=true면(정상 완료) 애초에 기록이 필요 없으므로 이 블록 자체를 건너뛴다.
  ROT_PART=""
  if [ "$COMPLETED" != "true" ]; then
    CURRENT_LINES=$(wc -l < docs/rotations.md 2>/dev/null | tr -d ' ')
    CURRENT_LINES=${CURRENT_LINES:-0}
    if [ "$CURRENT_LINES" -gt "$BASELINE" ] 2>/dev/null; then
      ROT_PART="완료 못 하고 멈춘 상태 — docs/rotations.md에 새 기록이 있습니다(확인하세요)"
    else
      ROT_PART="완료 못 하고 멈춘 상태인데 docs/rotations.md에 새 기록이 없습니다. 질문 후 같은 실행에서 이어갈 거면 무시해도 되지만, 아니면 왜 멈췄는지(회색지대/맴돌다 끊김/예산 소진) 지금 남기세요"
    fi
  fi

  # 4) 두 부분을 이어붙여 메시지 하나로 만든다(둘 중 하나만 있을 수도, 둘 다 있을 수도 있다).
  M="[Stop 훅] '$SKILL' 스킬"
  if [ -n "$TEST_PART" ]; then M="$M — $TEST_PART"; fi
  if [ -n "$ROT_PART" ]; then M="$M. $ROT_PART"; fi

  # 5) completed=true일 때만 state.json을 초기화한다 — 정말 끝난 실행만 다음 턴에 영향을
  #    안 주도록 지운다. completed=false면(회색지대 등으로 턴이 끊긴 채 다음 턴에서 같은
  #    스킬을 이어갈 수 있으므로) current_skill/rotations_baseline 등을 그대로 남겨,
  #    이어지는 턴들에서도 이 훅이 계속 추적할 수 있게 한다.
  if [ "$COMPLETED" = "true" ]; then
    echo '{}' > .claude/gate/state.json
  fi
  jq -cn --arg m "$M" '{hookSpecificOutput:{hookEventName:"Stop", additionalContext:$m}}'
fi
