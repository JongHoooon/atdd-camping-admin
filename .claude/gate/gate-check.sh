#!/usr/bin/env bash
# .claude/gate/armed 마커가 있을 때 Stop 훅이 호출하는 게이트 스크립트.
#
# 테스트 스위트를 돌린다. 성공하면 상태를 지우고 PASS를 기록한다.
# 실패하면 build/test-results/test/*.xml에서 실패한 테스트케이스 이름
# (ClassName#methodName)을 뽑아 state.json에 이름별/전체 카운터를 올리고
# 판정한다:
#   - 이름별 카운터가 NAME_LIMIT을 넘거나 전체 카운터가 TOTAL_LIMIT을
#     넘으면: HANDOFF 기록, state.json 삭제, exit 0 (사람에게 넘김)
#   - 아니면: BLOCK 기록, state.json 유지, 표준 오류에 상세 출력,
#     exit 2 (계속 막음)

set -u

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || exit 1

GATE_DIR=".claude/gate"
STATE_FILE="$GATE_DIR/state.json"
RECORD_LOG="$GATE_DIR/record.log"
RAW_LOG="$GATE_DIR/gate.log"

NAME_LIMIT=3
TOTAL_LIMIT=5

mkdir -p "$GATE_DIR"

TIMESTAMP() { date '+%Y-%m-%dT%H:%M:%S%z'; }

# 테스트 스위트를 돌리고 원문을 gate.log에 남긴다(매 실행마다 덮어씀).
{
  echo "===== gate-check 실행 시각: $(TIMESTAMP) ====="
  ./gradlew test --rerun-tasks
} >"$RAW_LOG" 2>&1
CODE=$?

if [ $CODE -eq 0 ]; then
  rm -f "$STATE_FILE"
  echo "$(TIMESTAMP) PASS" >>"$RECORD_LOG"
  exit 0
fi

# --- 실패 경로 ---
# 파이썬 프로세스 하나가 다음을 전부 처리한다: JUnit XML에서 실패한
# 테스트케이스 이름 파싱, state.json 카운터 갱신, 막을지/넘길지 판정,
# record.log 줄 기록, (막을 경우) 사람이 읽기 좋은 표준 오류 출력.
# 실패한 테스트케이스 이름은 환경변수로 넘긴다(GATE_RESULTS_DIR이 파이썬에
# XML 디렉터리를 직접 알려줌) — 파이썬 스크립트 자체가 표준입력으로
# 읽히므로 표준입력 충돌을 피하기 위함이다.
GATE_STATE_FILE="$STATE_FILE" \
GATE_RECORD_LOG="$RECORD_LOG" \
GATE_NAME_LIMIT="$NAME_LIMIT" \
GATE_TOTAL_LIMIT="$TOTAL_LIMIT" \
GATE_RESULTS_DIR="build/test-results/test" \
python3 - <<'PYEOF'
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def timestamp():
    return datetime.now().astimezone().strftime("%Y-%m-%dT%H:%M:%S%z")


def collect_failed_names(results_dir):
    # XML에서 실패한 테스트케이스 이름(classname#name)을 모두 모은다.
    names = []
    for path in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        root = tree.getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            for tc in suite.findall("testcase"):
                if tc.find("failure") is not None or tc.find("error") is not None:
                    classname = tc.get("classname", "")
                    name = tc.get("name", "")
                    names.append(f"{classname}#{name}")
    return names


def load_state(state_file):
    if os.path.exists(state_file):
        try:
            with open(state_file) as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            return {}
    return {}


def save_state(state_file, state):
    with open(state_file, "w") as f:
        json.dump(state, f, indent=2, sort_keys=True)
        f.write("\n")


def main():
    state_file = os.environ["GATE_STATE_FILE"]
    record_log = os.environ["GATE_RECORD_LOG"]
    name_limit = int(os.environ["GATE_NAME_LIMIT"])
    total_limit = int(os.environ["GATE_TOTAL_LIMIT"])
    results_dir = os.environ["GATE_RESULTS_DIR"]

    failed_names = collect_failed_names(results_dir)
    if not failed_names:
        # 테스트 실행은 실패했는데 XML에 실패한 <testcase>가 하나도 없는
        # 경우(예: 테스트가 돌기 전 빌드/컴파일 실패). 이 시도도 빠뜨리지
        # 않고 가상의 이름으로 막힌 횟수에 포함시킨다.
        failed_names = ["__unknown_failure__"]

    state = load_state(state_file)

    bumped = {}
    for name in failed_names:
        state[name] = state.get(name, 0) + 1
        bumped[name] = state[name]

    state["__total_blocks__"] = state.get("__total_blocks__", 0) + 1
    total_blocks = state["__total_blocks__"]

    over_name_limit = [n for n, c in bumped.items() if c >= name_limit]
    over_total_limit = total_blocks >= total_limit

    if over_name_limit or over_total_limit:
        reasons = [
            f"{n} 이(가) 이름별 한계({name_limit})를 {bumped[n]}번째로 넘김"
            for n in over_name_limit
        ]
        if over_total_limit:
            reasons.append(
                f"전체 막힌 횟수가 통째 한계({total_limit})를 "
                f"{total_blocks}번째로 넘김"
            )
        reason_text = "; ".join(reasons)

        try:
            os.remove(state_file)
        except FileNotFoundError:
            pass

        with open(record_log, "a") as f:
            f.write(f"{timestamp()} HANDOFF {reason_text}\n")

        print(
            f"[게이트] {reason_text} — 한계에 도달해 더 막지 않고 사람에게 넘깁니다.",
            file=sys.stderr,
        )
        sys.exit(1)

    # 아직 두 한계 다 안 넘었으면: 계속 막는다.
    save_state(state_file, state)

    summary_parts = [f"{n} (count={c})" for n, c in bumped.items()]
    summary_text = ", ".join(summary_parts)

    with open(record_log, "a") as f:
        f.write(f"{timestamp()} BLOCK {summary_text}\n")

    print("게이트가 이번 종료 시도를 막았습니다.", file=sys.stderr)
    print("실패한 테스트 이름과 각각 몇 번째로 막혔는지:", file=sys.stderr)
    for n, c in bumped.items():
        print(f"  - {n}: 지금까지 {c}번째로 막힘 (한계 {name_limit})", file=sys.stderr)
    print(
        f"지금까지 막힌 전체 횟수: {total_blocks} (한계 {total_limit})",
        file=sys.stderr,
    )

    sys.exit(2)


main()
PYEOF
exit $?
