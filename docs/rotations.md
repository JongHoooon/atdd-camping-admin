# 회전 기록

4단계(acceptance-criteria → acceptance-test → implementation → verdict) 스킬 중 하나가
완료 기준에 도달하지 못한 채 멈추려 할 때, 왜 멈췄는지를 스스로 판단해 한 줄 남긴다. 질문을
던지고 답을 받아 같은 실행 안에서 계속 이어간다면 남기지 않는다 — 이건 완료 기록이 아니라
실행이 끊긴 채로 사람에게 넘어갈 때 보여주는 기록이다. 자동으로 검사되는 게이트는 아니다.

멈춘 이유는 아래 3가지 중 하나로 분류한다(테스트가 실패한 것 자체는 이유가 아니다 — TDD
특성상 정상적인 실패일 수 있다):

| 범주 | 뜻 | 사람이 할 일 |
| --- | --- | --- |
| 회색지대 | 판단이 필요한 지점이라 물어본다 | 사양을 정해준다 |
| 맴돌다 끊김 | 같은 원인으로 제자리걸음이라고 스스로 판단됨 | 스킬 문서를 고친다 |
| 예산 소진 | 컨텍스트/턴 예산이 바닥나 못 끝냄 | 티켓을 더 잘게 쪼갠다 |

형식: `<시각> <티켓> <스킬>: <회색지대|맴돌다 끊김|예산 소진> — <무엇을 하다 왜 이 범주로 판단했는지>`

`implementation`의 안전망(`.claude/hooks/failure_gate.py`)은 `--tests`로 대상 클래스를 돌릴
때마다 결과를 아래 세 가지로 나눠 매번 `docs/rotations.md`에 한 줄씩 자동으로 남긴다 —
위 3범주와 달리 완료 기준 미달로 멈춘 기록이 아니라, 안전망 자체의 판정 로그다:

| 결과 | 조건 | 훅 동작 |
| --- | --- | --- |
| 막힘 | 실패했고 아직 한계(같은 이름 3회/총 5회) 안 | `exit 2`로 막고 이름·순번을 표준 오류로 알림. 상태 누적 |
| 넘김 | 실패했고 한계를 넘음 | `exit 0`으로 조용히 끝나 사람에게 넘김("맴돌다 끊김"에 해당). 상태 초기화 |
| 통과 | 실패 없음 | `exit 0`. 상태 초기화 |

형식: `<시각> <티켓> implementation: <막힘|넘김|통과> — <내용>`

이 형식은 2026-08-25 이후 적용한다.

---

00:02 T-5 implementation: 맴돌다 끊김 — 안전망(posttool-failure-guard.sh)이 발동해 멈춤.
숫자로 파싱할 수 없는 재고/가격, 정의되지 않은 productType 세 테스트가 같은 이유로 3번째
실패해 강제 개입함(구현을 단계적으로 나눠 진행하며 의도적으로 재현한 시나리오 — 훅 검증
목적). 안전망 자체는 정상 작동했으나, 그 과정에서 안전망이 처음엔 전혀 카운트를 못 하던
버그(JUnit 5 @Nested 클래스의 실패가 outer 클래스 XML이 아니라 TEST-<FQCN>$<Nested>.xml에
남는데 훅이 outer XML만 봤음)를 발견해 posttool-failure-guard.sh/count_failures.py를 먼저
고쳤다.
00:08 T-5 implementation: 진행 기록 — 실패: 숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다, 숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다, 재고를 음수로 수정하면 거부되고 재고는 그대로 유지된다, 이름을 빈 문자열로 수정하면 거부되고 이름은 그대로 유지된다, 가격을 음수로 수정하면 거부되고 가격은 그대로 유지된다, 정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다, 유효한 이름과 무효한 재고를 함께 수정하면 이름도 반영되지 않는다
00:09 T-5 implementation: 진행 기록 — 실패: 숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다, 숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다, 이름을 빈 문자열로 수정하면 거부되고 이름은 그대로 유지된다, 정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다
00:09 T-5 implementation: 맴돌다 끊김 — 안전망 3번째 시도 발동. 숫자로 파싱할 수 없는 재고/가격,
정의되지 않은 유형 세 테스트가 3번째로 같은 이유로 실패해 강제 개입함(음수 재고/가격,
빈 이름 검증은 이미 구현해 통과 — 유형/파싱 검증만 아직 안 넣은 상태로 반복 시도한 결과).
00:17 T-5 implementation: 막힘 — com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#재고를 음수로 수정하면 거부되고 재고는 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#이름을 빈 문자열로 수정하면 거부되고 이름은 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#가격을 음수로 수정하면 거부되고 가격은 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다 이(가) 1번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_유효한_필드와_무효한_필드가_함께_있으면_전체_거부되어야_한다$상품_수정#유효한 이름과 무효한 재고를 함께 수정하면 이름도 반영되지 않는다 이(가) 1번째로 실패
00:18 T-5 implementation: 막힘 — com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다 이(가) 2번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다 이(가) 2번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#이름을 빈 문자열로 수정하면 거부되고 이름은 그대로 유지된다 이(가) 2번째로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다 이(가) 2번째로 실패
00:18 T-5 implementation: 넘김 — com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다 이(가) 3번째로 같은 이유로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다 이(가) 3번째로 같은 이유로 실패; com.camping.admin.acceptance.ProductUpdateAcceptanceTest$T5_상품_수정_시_무효한_값은_거부되어야_한다$상품_수정#정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다 이(가) 3번째로 같은 이유로 실패
00:19 T-5 implementation: 통과 — 15개 테스트 전체 통과
00:19 T-5 implementation: 통과 — 10개 테스트 전체 통과
13:29 T-8 acceptance-criteria: 회색지대 — data.sql 시드 예약 12건을 다시 확인해 reservation_date/created_at 오프셋이 모두 일치함을 재확인. T-8 문구를 수정할 근거는 못 찾았고, 사용자가 놓친 관점이 있는지 확인 질문을 던진 채로 응답을 마쳐 다음 판단(문구 유지 vs 다른 관점 반영)이 사용자에게 넘어간 상태.
16:24 T-5 implementation: 통과 — 20개 테스트 전체 통과
