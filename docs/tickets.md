T-1 상품 수정이 저장되지 않음  
내용: 운영팀 신고 — "관리자 화면에서 상품 재고를 수정했는데, 저장했다고 나오고선 값이 그대로입니다."  
---

T-2 매출 리포트 금액이 화면마다 다름
내용: 운영팀 신고 — "매출 리포트 금액이 화면마다 다르게 나옵니다."
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

