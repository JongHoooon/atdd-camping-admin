#!/usr/bin/env python3
"""posttool-failure-guard.sh가 호출한다: JUnit XML의 실패 테스트케이스를
.claude/gate/failures.json에 누적하고, 이름별/총 한계를 넘으면 HANDOFF:<이유>를 출력한다.
넘지 않았으면 OK를 출력한다.
"""
import json
import sys
import xml.etree.ElementTree as ET

NAME_LIMIT = 3
TOTAL_LIMIT = 5


def main():
    xml_path, state_path = sys.argv[1], sys.argv[2]

    # 1) 방금 gradle이 만든 JUnit XML을 파싱해 실패한 테스트케이스 이름을 모은다.
    #    <testsuite>가 루트일 수도, 여러 <testsuite>를 감싼 형태일 수도 있어 둘 다 처리한다.
    tree = ET.parse(xml_path)
    root = tree.getroot()
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
    failed = []
    for suite in suites:
        for tc in suite.findall("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.append(f"{tc.get('classname', '')}#{tc.get('name', '')}")

    # 2) 이전까지 누적된 카운터를 불러온다(스킬 0단계에서 {"by_name":{},"total":0}으로 시작).
    with open(state_path) as f:
        state = json.load(f)

    # 3) 이번 실행이 전부 통과했으면 진전이 있었다는 뜻 — 누적 카운터를 리셋하고 끝낸다.
    if not failed:
        with open(state_path, "w") as f:
            json.dump({"by_name": {}, "total": 0}, f)
        print("OK")
        return

    # 4) 실패가 있으면 카운트를 올린다. by_name은 실패한 테스트마다 1씩,
    #    total은 이번 실행(호출) 1회당 1만 올린다 — 한 번에 여러 개가 같이 실패해도 total은 +1.
    for name in failed:
        state["by_name"][name] = state["by_name"].get(name, 0) + 1
    state["total"] = state.get("total", 0) + 1

    # 5) 방금 올린 값으로 한계(이름별 3 / 총 5) 초과 여부를 판정한다.
    over_name = [n for n, c in state["by_name"].items() if c >= NAME_LIMIT]
    over_total = state["total"] >= TOTAL_LIMIT

    if over_name or over_total:
        # 6a) 한계를 넘었으면 이유를 문장으로 만들고, 카운터를 리셋(이 판정 라운드는 끝)한 뒤
        #     posttool-failure-guard.sh가 exit 2로 쓸 수 있게 "HANDOFF:<이유>"를 표준출력한다.
        reasons = [
            f"{n} 이(가) {state['by_name'][n]}번째로 같은 이유로 실패" for n in over_name
        ]
        if over_total:
            reasons.append(f"이번 구현 시도 중 총 {state['total']}번째 실패")
        with open(state_path, "w") as f:
            json.dump({"by_name": {}, "total": 0}, f)
        print("HANDOFF:" + "; ".join(reasons))
    else:
        # 6b) 아직 한계 안이면 갱신된 카운터만 저장하고 "OK" — 호출부는 조용히 넘어간다.
        with open(state_path, "w") as f:
            json.dump(state, f)
        print("OK")


main()
