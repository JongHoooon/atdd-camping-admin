T-1 상품 수정이 저장되지 않음  
내용: 운영팀 신고 — "관리자 화면에서 상품 재고를 수정했는데, 저장했다고 나오고선 값이 그대로입니다."  
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

