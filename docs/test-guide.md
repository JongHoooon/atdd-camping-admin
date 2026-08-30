# Acceptance Test Guide

## 인증 관문 통과

`JwtAuthFilter`가 로그인/정적 자원 등 일부 경로를 뺀 전역(`/*`)에 걸려 있어, `/admin/**`
호출은 토큰 없이 보내면 401을 받는다(확인됨). `@BeforeEach`에서 `/auth/login`으로 로그인해
토큰을 받아 두고, 이후 모든 요청에 `Authorization: Bearer` 헤더로 붙인다.

```java
@BeforeEach
void setUp(TestInfo testInfo) {
    RestAssured.port = port;

    accessToken = given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "username": "admin",
                      "password": "admin123"
                    }
                    """)
            .when().post("/auth/login")
            .then().statusCode(200)
            .extract().path("accessToken");
}
```

로그인 계정(`admin`/`admin123`)은 `application.yml`의 `admin.username`/`admin.password`다.
매 요청마다 `.header("Authorization", "Bearer " + accessToken)`을 반복해서 붙인다 — 공유
헬퍼로 감싸지 않는다(`CLAUDE.md`가 지목한 이 저장소의 중복 허용 스타일과 같은 이유).


## 테스트 클린업

격리: 실제 서버로 호출하므로 상태가 남는다. 테스트는 자기 데이터를 따로 잡거나 `@Sql`로
초기화한다.

기본은 `@BeforeEach` 시점에 `deleteAll()`로 격리한다.

```java
@BeforeEach
void setUp(TestInfo testInfo) {
    repository.deleteAll();
    log.info("\n\n======== setUp completed — [{}] ========\n", testInfo.getDisplayName());
}
```

`deleteAll()` 이후 INFO 로그를 찍어 setUp 쿼리와 테스트 본문 쿼리를 로그에서 시각적으로 구분한다.

**`deleteAll()`을 쓸 수 없을 때(FK로 참조되는 시드 테이블 등)는 자기 데이터 영역 + `@Sql`로
초기화한다.** 테이블에 시드 row가 있고 다른 테이블이 그 시드를 FK로 참조하면 `deleteAll()`이
제약 위반으로 실패한다. 이때 테스트는 시드가 쓰지 않는 자기만의 id 범위(예: `id >= 1000`)에
데이터를 만들고, 클래스에 `@Sql` 두 개로 그 범위를 초기화/정리한다. SQL은 인라인 문자열이
아니라 `src/test/resources/sql/*.sql` 파일로 분리해 클래스패스에서 참조한다:

```java
@Sql(scripts = "classpath:sql/reset-product-identity-sequence.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:sql/delete-test-products.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductUpdateAcceptanceTest { ... }
```

H2 `GenerationType.IDENTITY`는 `data.sql`이 명시적으로 넣어 둔 id를 인식하지 못해, 그 범위와
겹치는 새 생성 요청이 충돌할 수 있다(확인됨). `BEFORE_TEST_METHOD`에서 시퀀스를 자기 데이터
영역의 시작값으로 매번 되돌리고, `AFTER_TEST_METHOD`에서 그 영역을 지워 다음 테스트가 항상
같은 조건에서 시작하게 한다.


## 테스트 네이밍

**"A이면 B이다" 형태의 완결된 문장. 비즈니스 정책을 드러낸다.**

| 수준           | 나쁜 예                                  | 좋은 예                               |
| ------------ | ------------------------------------- | ---------------------------------- |
| 명사 → 문장      | `30일 초과 예약 테스트`                       | `30일을 초과한 날짜로 예약하면 거부된다`           |
| 행위 → 행위 + 결과 | `예약을 거부한다`                            | `30일을 초과한 날짜로 예약 일정을 변경하면 거부된다`    |
| 구현 → 정책      | `POST: 오늘로부터 31일 뒤 예약은 거부한다 (경계 초과)` | `30일 초과 첫 경계 날짜로 예약하면 거부된다`        |

구현 세부사항(HTTP 메서드, 상태 코드 등)은 테스트명에 노출하지 않는다.


## 테스트 그룹화

관련 테스트는 `@Nested`로 묶어 두 단계 계층을 만든다. 그룹명은 테스트명과 같은 원칙(비즈니스 관점)을 따른다.

1단계: `acceptance-criteria.md`의 인수 조건 단위. 그룹명은 "티켓 번호: 요구사항 제목"으로 쓴다 —
`acceptance-criteria.md`로 바로 되짚어갈 수 있어야 한다.
2단계: 지금처럼 호출 메서드 단위(예약 생성/예약 수정).

```java
@Nested
@DisplayName("T-1: 예약 시작일은 오늘로부터 30일 이내여야 한다")
class T1_예약_시작일은_오늘로부터_30일_이내여야_한다 {

    @Nested
    @DisplayName("예약 생성")
    class 예약_생성 {

        @Test
        @DisplayName("오늘로부터 30일 이내 날짜로 예약하면 예약이 완료된다")
        void 오늘로부터_30일_이내_날짜로_예약하면_예약이_완료된다() { ... }
    }

    @Nested
    @DisplayName("예약 수정")
    class 예약_수정 {

        @Test
        @DisplayName("30일을 초과한 날짜로 예약 일정을 변경하면 거부된다")
        void 삼십일을_초과한_날짜로_예약_일정을_변경하면_거부된다() { ... }
    }
}
```


## API가 없어 Repository를 직접 쓸 때

생성 REST API가 없는 리소스는 RestAssured 대신 Repository로 데이터를 준비한다. 이때는
given/when/then 흐름이 호출 코드만 봐서는 드러나지 않으므로 `// given`/`// when`/`// then`
주석으로 표시한다. RestAssured 체이닝만으로 흐름이 이미 분명하면 붙이지 않는다.


## 테스트 수량

규칙 하나에 경계 실측 하나면 충분하다. 더 추가하고 싶으면 티켓으로 등록하고 다음으로 넘어간다.


## 같은 규칙의 여러 무효 값

"빈 문자열"과 "공백만"처럼 같은 규칙을 서로 다른 무효 값으로 검증할 때는 테스트를 따로 늘리지 않고
`@ParameterizedTest` + `@ValueSource`로 한 메서드에 묶는다.

```java
@ParameterizedTest
@ValueSource(strings = {"", "   "})
@DisplayName("전화번호가 빈 값이면 예약이 거부된다")
void 전화번호가_빈_값이면_예약이_거부된다(String blankPhoneNumber) { ... }
```
