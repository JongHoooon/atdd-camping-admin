---
name: acceptance-test
description: docs/plan.md 2단계(인수 테스트)를 처리한다. docs/acceptance-criteria.md와 docs/test-guide.md를 입력으로 받아 실패하는 인수 테스트 코드를 작성하고 실행해 판정한다. docs/acceptance-criteria.md에 없는 내용은 테스트에 넣지 않는다.
argument-hint: <티켓 ID> [테스트로 옮길 규칙]
---

# 인수 테스트 작성

`docs/plan.md`의 2단계("인수 테스트")를 처리한다. 이 스킬은 **2단계까지만** 담당한다.
구현(3단계, 프로덕션 코드 수정)에는 관여하지 않는다 — `src/main/java`는 건드리지 않는다.

## 멈추는 조건

이 스킬이 완료 기준에 도달하지 못한 채 멈추려 하면, 왜 멈추는지 아래 3가지 중 하나로 스스로
판단해 `docs/rotations.md`에 한 줄 남긴다(질문을 던지고 답을 받아 같은 실행 안에서 계속
이어간다면 남기지 않는다):

- **회색지대**: 판단이 필요한 지점이라 AskUserQuestion으로 묻고 멈춘다.
- **맴돌다 끊김**: 같은 원인으로 제자리걸음이라고 스스로 판단되면 멈추고 사람을 부른다 —
  테스트가 실패했다는 사실 자체는 이유가 아니다(TDD 특성상 정상적인 실패일 수 있다).
- **예산 소진**: 컨텍스트/턴 예산이 바닥나 완료 기준에 도달하지 못하고 멈춘다.

## 0. 입력 확인

아래 명령으로 `.claude/gate/state.json`에 이 스킬이 시작됐음을 기록한다 — Stop 훅이 이 스킬이
끝나는 시점에 테스트를 실제로 돌렸는지, 완료 못 하고 멈췄다면 `docs/rotations.md`에 기록을
남겼는지 확인하는 데 쓴다:

```bash
mkdir -p .claude/gate && jq -n --arg s "acceptance-test" --argjson rb "$(wc -l < docs/rotations.md | tr -d ' ')" '{current_skill:$s, completed:false, tests_verified:false, rotations_baseline:$rb}' > .claude/gate/state.json
```

인자로 티켓 ID(`T-n`)를 받는다.

- `docs/acceptance-criteria.md`에 해당 `# T-n 인수 조건` 블록이 없으면, 1단계
  (`.claude/skills/acceptance-criteria/SKILL.md`)가 아직 안 끝난 것이다. 먼저 그 스킬을
  실행하라고 안내하고 멈춘다.
- 티켓에 규칙 블록이 여러 개면 어떤 규칙을 테스트로 옮길지 확인한다. 인자로 이미 주어졌으면
  그대로 쓰고, 없으면 전체를 대상으로 하되 규칙마다 별도 파일/`@Nested` 클래스로 나눈다.
- 이 티켓에 대해 이미 테스트 파일이 있는지 테스트 소스 루트(`src/test/java` 아래, 이 프로젝트의
  테스트 패키지)에서 확인한다(Javadoc의 `T-n:` 표기로 검색). 있으면 새로 만들지 말고 기존 파일에
  이어 쓸지 확인한다.

## 1. 컨텍스트 로드

- `docs/principles.md`, `docs/plan.md`, `docs/test-guide.md`를 읽는다.
- `docs/acceptance-criteria.md`에서 대상 `# T-n 인수 조건`의 해당 규칙 블록만 읽는다.
- 테스트 소스 루트에 기존 인수 테스트 파일이 있으면 최소 1개 읽어 실제 코드 템플릿을 확인한다 —
  아래 3단계 템플릿과 실제 파일이 어긋나면 실제 파일을 우선한다(템플릿은 뼈대일 뿐이다). 아직
  하나도 없다면(이 프로젝트의 첫 인수 테스트) 아래 템플릿을 그대로 시작점으로 쓴다.

## 2. 규칙 → 테스트 매핑

대상 규칙 블록의 `When → Then` 줄을 순서대로 살핀다.

- **라벨이 `참고용`인 `When → Then` 줄은 테스트로 옮기지 않는다** — 이 규칙의 정식 조건이 아니다
  (`docs/plan.md` 2단계 AI 지시: "정식 요구사항으로 없는 내용은 테스트에 넣지 않는다"). 이전
  티켓에서 같은 라벨을 제외한 선례가 있으면 그 선례를 따른다.
- 나머지(`버그` / `정상` / `정상, 회귀 방지`) 줄은 원칙적으로 각각 테스트 메서드 하나로 옮긴다.
  작성 세부 규칙은 `docs/test-guide.md`를 참고한다.
- `acceptance-criteria.md`에 없는 새로운 경계값이나 케이스를 이 단계에서 만들어 끼워 넣지 않는다.
  필요하다고 판단되면 먼저 `acceptance-criteria.md`에 규칙으로 추가할지, `docs/tickets.md`에
  티켓으로 남길지 사용자에게 묻는다 — 조용히 추가하지 않는다. 티켓 형식은 `docs/plan.md`의
  "새 티켓 남기기"를 따른다.

## 3. 테스트 코드 작성

클린업, 네이밍, `@Nested` 그룹화, 시드 데이터 선택, 테스트 수량 등 세부 작성 규칙은
`docs/test-guide.md`를 참고해 작성한다. 기존 인수 테스트 파일이 있으면 그 구조를 그대로
재현한다. 없다면 아래 스켈레톤을 시작점으로 쓴다 — 각괄호(`< >`)는 이 티켓과 이 프로젝트에
맞게 채워 넣을 자리다:

```java
package <이 프로젝트의 테스트 소스 패키지>;

import <이 요구사항이 다루는 Repository>;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-n: <규칙 한 줄>
 *
 * 인수 조건: docs/acceptance-criteria.md
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class <요구사항 주제를 서술하는 영문 이름>AcceptanceTest {

    @LocalServerPort
    int port;

    @Autowired
    <Repository 타입> <repository 변수명>;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        RestAssured.port = port;
        <repository 변수명>.deleteAll();
        log.info("\n\n======== setUp completed — [{}] ========\n", testInfo.getDisplayName());
    }

    @Nested
    @DisplayName("T-n: <acceptance-criteria.md의 규칙 문구 그대로>")
    class Tn_<스네이크케이스_제목> {

        @Nested
        @DisplayName("<정책이 적용되는 첫 번째 경로 — 예: 생성>")
        class <경로1_이름> { /* 첫 번째 경로의 케이스들 */ }

        @Nested
        @DisplayName("<정책이 적용되는 두 번째 경로 — 예: 수정>")
        class <경로2_이름> { /* 두 번째 경로 케이스들 — 필요하면 먼저 첫 번째 경로로 데이터를
                                만들고 응답에서 식별자를 추출해 사용 */ }
    }
}
```

- 파일명: 요구사항 주제를 서술하는 영문 PascalCase + `AcceptanceTest` (티켓 번호가 아니라 주제
  기준). 이 프로젝트에 이미 같은 이름 규칙을 쓴 파일이 있으면 그 선례를 따른다. 기존 파일과
  주제가 겹치면 새 파일을 만들지 않고 그 안에 `@Nested` 클래스를 추가한다.
- HTTP 호출: RestAssured
  (`given().contentType(ContentType.JSON).body("""...""".formatted(...)).when().post/put(...).then().statusCode(...)`).
  텍스트 블록(`"""`)으로 JSON 바디를 쓴다.
- 두 번째 이후 경로(예: 수정)가 식별자를 필요로 하면 먼저 첫 번째 경로(예: 생성)를 호출해 응답에서
  식별자를 추출한 뒤 그 경로에 사용한다.
- 상태 코드: `CLAUDE.md`나 `docs/test-guide.md`에 이미 정리된 컨벤션이 있으면 그것을 따른다.
  없으면 `docs/acceptance-criteria.md`에 실측해 둔 응답 코드를 그대로 assert한다 — 새로 상태
  코드 체계를 만들지 않는다.
- 공유 베이스 테스트 클래스를 새로 만들지 않는다. 이 저장소가 의도적으로 중복을 허용하는
  스타일인지는 `CLAUDE.md`를 확인한다. `docs/test-guide.md`의 클린업 패턴을 클래스마다 그대로
  반복한다.
- **`data.sql` 시드 row를 그대로 수정/재사용하지 않는다.** 테스트에 필요한 데이터는 해당 리소스의
  생성 엔드포인트로 직접 만든다. 시드 row를 재사용하면 테스트끼리 같은 row를 공유해 순서
  의존성이 생기고, `acceptance-criteria.md`의 `Given`도 시드 값을 쓰지 않는 쪽으로 이미 맞춰져
  있다(`.claude/skills/acceptance-criteria/SKILL.md` 참고). 시드 row 자체가 검증 대상인
  티켓(예: 시드 데이터 무결성 버그)만 예외다.
- 시드가 있는 테이블은 생성 시 두 가지를 미리 확인한다: (1) 다른 테이블이 FK로 참조하고 있으면
  `deleteAll()`이 제약 위반으로 실패할 수 있다. (2) `data.sql`이 명시적 `id`로 미리 넣어 둔
  값과 JPA `GenerationType.IDENTITY` 시퀀스가 동기화되지 않아, 새로 만드는 첫 몇 건의 생성
  요청이 시드 id와 충돌해 실패할 수 있다(H2 확인됨).
  `deleteAll()`을 쓸 수 없으면 `docs/test-guide.md`의 "테스트 클린업" 절을 따른다: 테스트는
  시드가 쓰지 않는 자기만의 id 범위(예: `id >= 1000`)에 데이터를 만들고, 클래스에 `@Sql`
  두 개를 붙여 그 범위를 초기화/정리한다 — `BEFORE_TEST_METHOD`에서 시퀀스를 그 범위의
  시작값으로 되돌리고, `AFTER_TEST_METHOD`에서 그 범위를 지운다. SQL은 인라인 문자열이 아니라
  `src/test/resources/sql/*.sql` 파일로 분리해 `classpath:sql/...`로 참조한다(예:
  `src/test/java/com/camping/admin/acceptance/ProductUpdateAcceptanceTest.java`와
  `src/test/resources/sql/reset-product-identity-sequence.sql`,
  `src/test/resources/sql/delete-test-products.sql` 참고).

## 4. 완료 기준

새/수정된 테스트 클래스를 실행해(명령은 `docs/plan.md`의 "이 저장소의 실행 방법" 중 "특정
테스트 클래스만 실행"), 라벨별 기대 결과와 실제 결과가 일치하면 완료다.

테스트를 실행했으면(결과의 통과/실패와 무관하게) 아래 명령으로 실제로 돌렸다는 사실을
`.claude/gate/state.json`에 기록한다:

```bash
jq '.tests_verified=true' .claude/gate/state.json > .claude/gate/state.json.tmp && mv .claude/gate/state.json.tmp .claude/gate/state.json
```

결과를 못 읽으면 라벨과 상관없이 완료로 치지 않는다(`docs/principles.md` "확인이 안 되면
통과가 아니다").

라벨별 완료 조건은 서로 반대 방향이다:

- `버그` 라벨 테스트는 **지금 실패해야 완료다.** 통과하면 지금 동작(버그)을 겨눈 초안이라는
  뜻이므로, 무엇을 assert했는지 다시 보고 원하는 동작(status code, 메시지)을 assert하도록
  고친 뒤 다시 실행한다.
- `정상` / `정상, 회귀 방지` 라벨 테스트는 **지금 통과해야 완료다** (회귀 방지 목적).
  실패하면 `acceptance-criteria.md`의 실측 기록과 지금 코드 상태가 어긋난 것이므로, 조용히
  assert를 바꾸지 말고 사용자에게 알린다.
- 테스트별 결과를 요약해 보고한다 (메서드명 → PASS/FAIL → 기대와 일치 여부).
- 모든 라벨이 기대와 일치해 진짜로 완료됐으면 아래 명령으로 `completed`를 표시한다:

```bash
jq '.completed=true' .claude/gate/state.json > .claude/gate/state.json.tmp && mv .claude/gate/state.json.tmp .claude/gate/state.json
```

## 5. `docs/test-guide.md` 준수 확인 및 갱신 질문

- 작성한 테스트가 `docs/test-guide.md`의 규칙을 지켰는지 스스로 점검한다.
- 작성하며 `test-guide.md`에 아직 없는 새로운 제약(예: 새로운 종류의 경계, 새로운 준비 패턴)을
  스스로 정했다면, 조용히 코드에만 반영하지 않고 AskUserQuestion으로 `test-guide.md`에 규칙 한 줄로
  추가할지 묻는다 (`docs/plan.md` 2단계 AI 지시).

## 6. 마무리 보고

- 어떤 티켓/규칙을 다뤘는지, 어떤 파일을 새로 만들었거나 수정했는지, 테스트 메서드가 몇 개고
  각각 PASS/FAIL이 기대와 일치했는지, `참고용` 라벨이라 제외한 예시가 있는지, `test-guide.md`에
  새로 제안한 규칙이 있는지 요약한다.
- 프로덕션 코드(`src/main/java`)는 건드리지 않았는지 확인한다 — 3단계(구현)는 이 스킬의 범위가
  아니다.
