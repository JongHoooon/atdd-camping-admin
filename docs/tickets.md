T-1 상품 수정이 저장되지 않음  
내용: 운영팀 신고 — "관리자 화면에서 상품 재고를 수정했는데, 저장했다고 나오고선 값이 그대로입니다."  
---

T-2 매출 리포트 금액이 화면마다 다름
내용: 운영팀 신고 — "매출 리포트 금액이 화면마다 다르게 나옵니다."
---

T-3 취소한 예약이 매출에 그대로 남음
내용: 운영팀 신고 — "예약을 취소 처리했는데 그날 매출이 안 줄어듭니다."
---

T-4 존재하지 않는 상품 id로 수정 시 500 응답

내용: T-1 작업 중 확인. PUT /admin/products/{id}에서 productId가 존재하지 않으면
ProductAdminController.updateProduct(controller/ProductAdminController.java:110-111)가
IllegalArgumentException을 던지는데, 이를 잡아 404로 변환하는 처리가 없어 스프링 기본 에러
핸들러가 500 Internal Server Error로 응답한다(직접 호출로 실측: PUT /admin/products/9999 →
500 `{"error":"Internal Server Error",...}`). 존재하지 않는 리소스 수정 시 어떤 상태 코드를
반환해야 하는지는 요구사항이 침묵하므로 이 티켓에서는 판단하지 않는다.

---

T-5 상품 수정 시 값 검증 정책이 없음

내용: T-1 작업 중 확인. PUT /admin/products/{id}에서 ProductAdminController.updateProduct
(controller/ProductAdminController.java:106-154)는 필드값에 대한 검증이 전혀 없다. 직접
호출로 실측:
- 음수 stockQuantity: {"stockQuantity":-5} → 200, stockQuantity:-5로 그대로 반영
- 음수 price: {"price":-1000} → 200, price:-1000으로 그대로 반영
- 빈 문자열 name: {"name":""} → 200, name:""로 그대로 반영
- 파싱 불가능한 값: {"stockQuantity":"abc"} → 200, 에러 없이 조용히 무시되고 필드는 안 바뀜.
  {"productType":"FOO"} → 200, 마찬가지로 조용히 무시됨
이런 값을 거부해야 하는지, 거부한다면 어떤 상태 코드/메시지를 반환해야 하는지는 요구사항이
침묵하므로 이 티켓에서는 판단하지 않는다. 다만 T-1(저장 버그)이 고쳐지기 전에는 이 값들이
DB에 실제로 반영되지 않아 드러나지 않았을 뿐, T-1을 save() 호출만 추가해 고치면 이 값들이
검증 없이 그대로 저장되게 되므로 T-1 구현과 함께 검토가 필요하다.

---

T-6 상품 수정 로직을 ProductService로 통합할지 검토

내용: T-1 작업 중 논의. 현재 상품 수정 로직은 ProductAdminController.updateProduct와
ConsoleProductController.update 두 곳에 각각 구현돼 있고(엔티티 조회 → setter로 필드 반영 →
save()), T-1에서 빠져 있던 save() 호출을 두 곳 모두에 추가하는 방식으로 고쳤다. 이 중복을
ProductService의 메서드 하나로 통합할지 논의했으나, CLAUDE.md가 이 저장소의 서비스 계층은
"부분적으로만 쓰이고 일관성이 없다"고 명시하고 티켓 범위를 넘어선 정리를 금지하고 있어 T-1
범위에서는 보류하고 별도 티켓으로 분리했다. 통합할 가치가 있는지, 있다면 두 컨트롤러 모두
서비스를 거치도록 바꿀지(웹 콘솔 흐름도 포함할지)는 요구사항이 침묵하므로 판단하지 않는다.

참고: T-1에서 명시적 save() 호출로 고친 이유는, 두 컨트롤러 메서드 모두 @Transactional이 없어
엔티티 조회(findById)와 setter 호출이 서로 다른(또는 없는) 트랜잭션 경계에 걸쳐 있고, 그 사이엔
flush를 일으킬 트랜잭션 커밋이 없어 JPA 더티 체킹이 작동하지 않기 때문이다. ProductService로
로직을 옮기면서 그 메서드에 @Transactional을 붙이면, 메서드 종료 시 커밋되며 더티 체킹만으로도
저장되어 명시적 save() 호출 없이 해결할 수 있다 — 이 방식도 통합 여부를 판단할 때 함께
검토한다.

---

T-7 예약 매출이 캠핑장 실제 요금과 무관하게 1박당 50,000원으로 고정됨

내용: T-2(매출 리포트 금액 불일치) 작업 중 확인. SalesService의 세 리포트 메서드
(generateDailyRevenueReport, generateRangeRevenueReport, generateRangeRevenueEntries, 모두
service/SalesService.java)는 예약 매출을 실제 요금이 아니라 `(박수) * 50000원`으로 고정
계산한다. Reservation과 Campsite 엔티티 어디에도 요금 필드가 없어 캠핑장별 실제 가격이
얼마인지 저장돼 있지 않다. 예약 매출 계산이 캠핑장별 실제 요금을 반영해야 하는지, 반영한다면
요금을 어디에 새로 저장해야 하는지는 요구사항이 침묵하므로 이 티켓에서는 판단하지 않는다.

---

T-8 매출 상세내역에서 예약 항목의 표시 날짜가 집계 기준 날짜와 다른 필드를 씀

내용: T-2 작업 중 확인. SalesService.generateRangeRevenueEntries(service/SalesService.java:
118-148)는 예약을 reservationDate 기준으로 필터링해 조회 기간에 포함시키면서도, 목록 항목에
채우는 occurredAt은 reservationDate가 아니라 r.getCreatedAt()을 쓴다(146번 줄). 두 값이 다른
예약이 있다면 같은 예약이 "이 기간에 포함된 거래"로 집계되면서도 화면에는 그 기간 밖의 날짜로
표시될 수 있다.

이 저장소는 README.md에 "관리자를 위한 통합 관리 시스템"으로 명시돼 있고 주요 기능도 "예약
관리"(조회/상태변경)이지 "예약 접수"가 아니다 — 즉 고객이 실제로 예약을 만드는 흐름은 이
저장소 밖의 별도 시스템(고객용 예약 페이지) 몫으로 보이고, ReservationAdminController에
생성(POST) 엔드포인트가 없는 것은 버그가 아니라 이 프로젝트의 의도된 경계일 가능성이 크다.
그래서 이 티켓에서는 reservationDate와 createdAt이 다른 예약을 이 저장소의 API로 직접 만들어
curl로 재현할 방법이 없었다 — data.sql 시드 예약은 모두 두 값이 같은 날짜로 맞춰져 있다. 이
불일치가 실제 운영 데이터(고객용 시스템이 만드는 예약)에서 발생하는지, 발생한다면 이 admin
쪽에서 어느 필드를 표시에 써야 하는지는 이 저장소만으로는 확인할 수 없어 판단하지 않는다.

---

T-9 서버 기동 직후 대여/판매 등록 API가 몇 차례 500 응답을 반환함

내용: T-2 작업 중 확인. `POST /admin/rentals`, `POST /api/sales`를 서버 기동 직후 호출하면
처음 몇 번은 `{"status":500,"error":"Internal Server Error"}`를 반환하고(직접 호출로 실측:
rental_records는 5회, sales_records는 4회 연속 500 이후 정상 응답), 서버 로그엔
"Unique index or primary key violation" H2 예외가 찍힌다. `data.sql`이 rental_records/
sales_records에 명시적 id로 로우를 넣어 두는데(rental_records 1~6, sales_records 1~5) H2의
identity 카운터가 그 explicit id들을 반영하지 못해 시드 최대 id 근처에서 겹치는 값을
발급하다가, 몇 번 실패한 뒤에야 시드 범위를 넘어서며 정상 동작한다. 재현: 서버를 새로
기동한 직후 위 두 엔드포인트를 연달아 호출하면 항상 재현됨. 이 문제를 이 티켓(T-2)에서
함께 고칠지, 별도로 처리할지, data.sql의 시드 방식(explicit id) 자체를 바꿀지는 요구사항이
침묵하므로 이 티켓에서는 판단하지 않는다.

---

T-10 거래 당시 상품 정보를 스냅샷으로 남기는 별도 테이블 도입 검토

내용: T-2 구현 중 논의. `SalesRecord`/`RentalRecord`는 각각 상품을 FK로만 참조하고
거래 시점에 계산한 금액(`totalPrice`)만 저장한다(T-2에서 `RentalRecord`에도 이 필드를
추가함). 상품명이 나중에 바뀌거나, 한 거래에 여러 품목이 묶이는 주문(장바구니)을 다뤄야
하는 경우 지금 구조로는 거래 당시의 이름·구성을 복원할 수 없다. 이런 요구가 실제로
있는지, 있다면 `SalesRecord`/`RentalRecord`를 거래 스냅샷을 담는 별도 테이블/구조로
바꿀지는 요구사항이 침묵하므로 이 티켓에서는 판단하지 않는다.

필요: 상품명 변경 이력 보존이나 다건 품목 주문이 실제 업무 요구사항인지 먼저 확인한다.
확인되면 `SalesRecord`/`RentalRecord` 양쪽을 함께 바꿀지, 새 테이블을 도입할지 설계를
결정한다.

---

T-11 취소 외 예약 상태의 매출 반영 여부가 정해지지 않음

내용: T-3 작업 중 확인. `SalesService`의 세 리포트 메서드(generateDailyRevenueReport,
generateRangeRevenueReport, generateRangeRevenueEntries, 모두 service/SalesService.java)는
예약을 reservationDate로만 걸러 매출에 합산하고 상태는 전혀 보지 않는다. T-3에서는 CANCELLED
상태만 매출에서 빼기로 확정했지만, 그 외 상태(WAITING/PENDING/REJECTED/CHECKED_IN/
CHECKED_OUT)를 매출에 포함해야 하는지 제외해야 하는지는 요구사항이 침묵하므로 T-3 범위에서는
판단하지 않는다.

필요: 상태별 매출 반영 정책을 정한다 — 예: 확정 전 단계인 WAITING/PENDING도 매출로 잡아야
하는지, 예약을 거절한 REJECTED나 이미 체크인/체크아웃한 CHECKED_IN/CHECKED_OUT은 어떻게
다뤄야 하는지. 정책이 정해지면 어떤 상태를 어느 리포트에서 제외할지 확인한다.

---

T-12 예약 상태가 enum 없이 매직 스트링으로 다뤄짐

내용: T-3 구현 중 확인. `domain/enums/ReservationStatus`에 WAITING/PENDING/CONFIRMED/
REJECTED/CHECKED_IN/CHECKED_OUT/CANCELLED enum이 정의돼 있지만, `Reservation` 엔티티의
`status` 필드(domain/entity/Reservation.java:39)는 `@Enumerated` 없는 순수 `String`이라
이 enum을 실제로 참조하는 곳이 프로젝트 전체에 하나도 없다. 대신 `"CANCELLED"`/`"CONFIRMED"`
같은 문자열 리터럴이 여러 곳에 흩어져 있다(`Reservation.java:50`의 `@PrePersist` 기본값,
`ReservationRepository.java:18`의 JPQL, `ReservationAdminController.java:59-66`의 PATCH
처리 — 여기는 값 검증도 없어 enum에 없는 임의 문자열도 그대로 저장됨, T-3에서 추가한
`SalesService.java`의 매출 필터 세 곳도 같은 관례를 따름). 이 불일치를 지금 고칠지는
요구사항이 침묵하므로 이 티켓에서는 판단하지 않는다.

필요: 엔티티 필드를 `@Enumerated(EnumType.STRING)` + `ReservationStatus` 타입으로 바꾸고
매직 스트링을 쓰는 위 지점들을 enum 참조로 교체할지 결정한다. `ReservationAdminController`의
값 검증 부재(T-11과 별개로, enum에 없는 값 자체를 거부할지)도 함께 볼지 정한다.

---

T-13 상품 수정 웹 콘솔 화면에 T-5 검증이 전혀 적용되지 않음

내용: T-5 작업(문자열 형태의 음수 재고/가격이 JSON API 검증을 우회하는 버그를 고치던 중)
확인. `ConsoleProductController.update`(web/ConsoleProductController.java:76-103)는
`ProductAdminController.updateProduct`와 같은 "상품 수정" 정책을 다루는 병렬 경로(CLAUDE.md가
명시한 의도된 중복)인데, T-5가 JSON API 쪽에만 적용되고 이 경로에는 검증이 전혀 구현돼 있지
않다. 음수 재고/가격, 빈 이름, 정의되지 않은 유형, 파싱 불가능한 값 모두 에러 없이 조용히
무시되거나(파싱 실패) 그대로 저장된다(성공 케이스). 웹 콘솔 화면에도 같은 검증 정책을
적용해야 하는지, 적용한다면 400 JSON 대신 이 경로의 관례(폼 리다이렉트 + flash 메시지)로
표현해야 하는지는 요구사항이 침묵하므로 이 티켓에서는 판단하지 않는다.

필요: 웹 콘솔 수정 화면에도 값 검증을 적용할지 정책을 정한다. 적용한다면 거부 시 사용자에게
보여줄 방식(현재 폼 리다이렉트+flash 관례를 따를지, 다른 방식을 쓸지)도 함께 정한다.

추가 결정: PR 리뷰에서 저장 누락 수정(T-1)은 두 컨트롤러를 함께 고쳤는데 검증은 한쪽에만
들어간 불일치를 지적받았다. 검증 정책이 정해지지 않은 상태로 콘솔 경로의 저장(save() 호출)만
남겨두면 미검증 값이 그대로 DB에 반영되므로, 이 티켓에서 검증 정책을 정하기 전까지
`ConsoleProductController.update`의 `save()` 호출을 되돌렸다(T-1 저장 버그가 콘솔 경로에
한해 다시 열린 상태). T-13에서 검증 정책과 함께 저장 로직을 다시 추가한다.

---

