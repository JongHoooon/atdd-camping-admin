# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code(claude.ai/code)에게 제공하는 가이드다.

## 프로젝트 개요

가상의 캠핑장 체인("초록 캠핑장")을 위한 Spring Boot 관리자 백엔드다. 예약, 사이트, 상품(판매/대여),
매출 기능을 다룬다. 이 프로젝트는 **ATDD(인수 테스트 주도 개발) 연습을 위해 의도적으로 만든
레거시 코드 교육용 프로젝트**다(`README.md` 참고) — 중복과 일관성 없는 패턴이 의도적으로 들어있다.
티켓이 요구하는 범위를 넘어서 코드를 "정리"하지 않는다.

## 작업 방식 (티켓을 시작하기 전에 읽을 것)

이 저장소는 4단계 ATDD 루프(인수 조건 → 인수 테스트 → 구현 → 판정)를 따른다. 순서와 각 단계에서
할 일은 `docs/plan.md`, 지켜야 할 원칙은 `docs/principles.md`를 참고한다.

기타 문서:
- `docs/tickets.md` — 열려 있는 티켓/버그 신고
- `docs/acceptance-criteria.md` — 티켓별로 확정된 규칙 (현재 비어 있음 — 티켓을 진행하며 채운다)
- `docs/retrospective.md` — 작업 방식에 대한 회고
- `design.md` — Thymeleaf 관리자 화면의 CSS/레이아웃 컨벤션(폰트, 버튼, 카드, 테이블, 색상)

## 커맨드

```bash
# 서버 실행 (기본 8080 포트, 사용 중이면 포트를 바꿔서 실행)
./gradlew bootRun
SERVER_PORT=8081 ./gradlew bootRun

# 전체 테스트 실행
./gradlew test
# 결과가 캐시되어 안 보이면 강제로 다시 돌린다:
./gradlew test --rerun-tasks
```

현재 `src/test` 디렉터리는 없다 — 위 인수 테스트 단계에 따라 티켓마다 테스트를 추가한다.
Cucumber(`io.cucumber:cucumber-java`, JUnit Platform Suite), RestAssured(`spring-mock-mvc` 포함),
`spring-boot-starter-test`는 필요할 때 쓸 수 있도록 이미 테스트 클래스패스(`build.gradle`)에
있다.

데이터에 의존하는 테스트를 작성하거나 판단하기 전에 `src/main/resources/data.sql`을 직접 읽는다 —
시드 데이터(상품, 사이트, 예약, 판매/대여 기록)의 근거는 `test-guide.md`의 서술이 아니라 이 파일이다.

- 앱: http://localhost:8080 (또는 `$SERVER_PORT`)
- H2 콘솔: `/h2-console` — JDBC URL `jdbc:h2:mem:testdb`, 사용자 `sa`, 비밀번호 없음
- 로그인: 아이디 `admin`, 비밀번호 `admin123` (`application.yml`의 `admin.username`/`admin.password`)
- DB는 매번 부팅할 때마다 재생성된다 (`spring.jpa.hibernate.ddl-auto=create-drop` + 시작 시마다
  `data.sql` 재실행)

## 테스트 컨벤션

테스트 격리, 네이밍, `@Nested` 그룹화, 경계값 테스트 수량, 시드 데이터 관련 규칙은
`docs/test-guide.md`를 참고한다.

## 아키텍처

패키지 루트: `com.camping.admin`.

**두 개의 병렬 컨트롤러 계층이 각자 같은 리포지토리를 직접 호출한다** — 이 중복은 (레거시 코드
교육을 위해) 의도된 것이며, 보존하거나 확장해야 할 공통 추상화가 아니다.
- `controller/*AdminController` — `/admin/**`와 `/auth` 하위의 `@RestController`들. JSON 입출력을
  담당하는 API 표면이다. 다수가 요청 바디를 타입이 있는 DTO 바인딩 대신 원시 `Map<String, Object>`로
  받아 필드마다 수동으로 타입 변환/try-catch를 수행한다(예: `ProductAdminController`).
- `web/Console*Controller` — `/console/**` 하위의 `@Controller`들, 서버 렌더링 Thymeleaf 뷰를 반환한다.
  폼 전송을 `Map<String, String>`으로 받아 같은 방식의 수동 변환을 한다.

두 계층 모두 `service/*`를 거치지 않고 `repository`를 직접 호출하는 경우가 많다 — 서비스 계층
(`ProductService`, `RentalService`, `SalesService`)은 부분적으로만 쓰이고 일관성이 없으므로,
비즈니스 로직이 서비스에 있다고 가정하지 말고 호출하는 컨트롤러도 함께 확인한다.

**인증**: `security/JwtAuthFilter`가 `config/WebConfig`를 통해 전역(`/*`)으로 등록되어 Spring MVC
디스패치 이전에 실행된다. `Authorization: Bearer` 헤더 또는 `AUTH_TOKEN` 쿠키에서 JWT를 받으며,
요청의 `Accept` 헤더에 따라(HTML 요청이면 `/login`으로 리다이렉트, API 호출이면 401 JSON 응답)
다르게 처리한다. 제외 경로(로그인, 정적 자원, `/h2-console/**`, `/`)는 `JwtAuthFilter.isExcluded`에
하드코딩되어 있다. 로그인은 `controller/AuthController`(`POST /auth/login`)가 발급하며, `AUTH_TOKEN`
쿠키를 설정하고 토큰을 JSON 바디로도 반환한다. Spring Security 의존성은 없다 — 인증은 이 수작업
필터 하나와 `security/JwtService`(jjwt)로만 이루어진다.

**도메인**: `domain/entity`의 JPA 엔티티들(`Campsite`, `Product`, `Reservation`, `Customer`,
`SalesRecord`, `RentalRecord`)과 `domain/enums`의 열거형들(`CampsiteStatus`, `ProductType`,
`ReservationStatus`). `ProductType`은 `SALE`과 `RENTAL`을 구분하며, 이것이 판매 흐름
(`SalesService`/`SalesController`)과 대여 흐름(`RentalService`/`RentalAdminController`,
`RentalRecord.isReturned`로 반납 여부 추적)을 각각 결정한다.
