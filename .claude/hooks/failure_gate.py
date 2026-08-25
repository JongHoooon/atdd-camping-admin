#!/usr/bin/env python3
"""posttool-failure-guard.sh가 호출하는 게이트 스크립트. JUnit XML을 읽어 실패한
테스트케이스 이름별 횟수를 상태 파일(.claude/gate/failures.json)에 센다.

- 테스트가 실패했는데 아직 한계(이름별/총) 안이면: "막힘" — exit 2로 막고, 실패한
  이름과 지금 몇 번째인지를 표준 오류로 알린다. 상태는 지우지 않고 누적한다.
- 실패했고 이름별 한계(NAME_LIMIT)나 총 한계(TOTAL_LIMIT)를 넘었으면: "넘김" — exit 0으로
  끝내 사람에게 넘긴다. 상태를 지운다.
- 이번 실행에 실패가 하나도 없으면: "통과" — exit 0. 상태를 지운다.

세 경우 모두 docs/rotations.md에 한 줄씩 남긴다(막힌 줄/넘긴 줄/통과한 줄).
"""
import datetime
import json
import sys
import xml.etree.ElementTree as ET

NAME_LIMIT = 3
TOTAL_LIMIT = 5
ROTATIONS_PATH = "docs/rotations.md"


def append_line(ticket, label, detail):
    time = datetime.datetime.now().strftime("%H:%M")
    with open(ROTATIONS_PATH, "a") as f:
        f.write(f"{time} {ticket} implementation: {label} — {detail}\n")


def collect_failures(xml_paths):
    # JUnit 5는 @Nested 클래스가 있으면 outer 클래스 이름의 XML(TEST-<FQCN>.xml)엔
    # testcase가 안 남고, 중첩 클래스마다 TEST-<FQCN>$<Nested>.xml로 쪼개 남긴다 — 이
    # 저장소 인수 테스트는 전부 @Nested를 쓰므로(test-guide.md) 호출부가 그 파일들을
    # 전부 찾아 넘겨준다. <testsuite>가 루트일 수도, 여러 <testsuite>를 감싼 형태일 수도
    # 있어 둘 다 처리한다.
    failed, total_cases = [], 0
    for xml_path in xml_paths:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            for tc in suite.findall("testcase"):
                total_cases += 1
                if tc.find("failure") is not None or tc.find("error") is not None:
                    failed.append(f"{tc.get('classname', '')}#{tc.get('name', '')}")
    return failed, total_cases


def main():
    state_path, ticket = sys.argv[1], sys.argv[2]
    xml_paths = sys.argv[3:]

    failed, total_cases = collect_failures(xml_paths)

    with open(state_path) as f:
        state = json.load(f)

    # 통과: 이번 실행에 실패가 하나도 없다 — 상태를 지우고 통과 기록을 남긴다.
    if not failed:
        with open(state_path, "w") as f:
            json.dump({"by_name": {}, "total": 0}, f)
        append_line(ticket, "통과", f"{total_cases}개 테스트 전체 통과")
        return 0

    # 실패가 있으면 카운트를 올린다. by_name은 실패한 테스트마다 1씩, total은 이번
    # 실행(호출) 1회당 1만 올린다 — 한 번에 여러 개가 같이 실패해도 total은 +1.
    for name in failed:
        state["by_name"][name] = state["by_name"].get(name, 0) + 1
    state["total"] = state.get("total", 0) + 1

    over_name = [n for n, c in state["by_name"].items() if c >= NAME_LIMIT]
    over_total = state["total"] >= TOTAL_LIMIT

    if over_name or over_total:
        # 넘김: 이름별 또는 총 한계를 넘었다 — 더 이상 Claude를 exit 2로 막지 않고
        # 조용히(exit 0) 끝내 사람에게 넘긴다. 상태를 지운다.
        reasons = [f"{n} 이(가) {state['by_name'][n]}번째로 같은 이유로 실패" for n in over_name]
        if over_total:
            reasons.append(f"총 {state['total']}번째 실패")
        with open(state_path, "w") as f:
            json.dump({"by_name": {}, "total": 0}, f)
        append_line(ticket, "넘김", "; ".join(reasons))
        return 0
    else:
        # 막힘: 아직 한계 안이지만 실패했다 — exit 2로 막고 이름과 지금 몇 번째인지
        # 표준 오류로 알린다. 상태는 누적한 채로 저장한다(지우지 않는다).
        reasons = [f"{n} 이(가) {state['by_name'][n]}번째로 실패" for n in failed]
        detail = "; ".join(reasons)
        with open(state_path, "w") as f:
            json.dump(state, f)
        append_line(ticket, "막힘", detail)
        print(detail, file=sys.stderr)
        return 2


sys.exit(main())
