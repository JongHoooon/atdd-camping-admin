package com.camping.admin.acceptance;

import com.camping.admin.domain.entity.Reservation;
import com.camping.admin.repository.CampsiteRepository;
import com.camping.admin.repository.ReservationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-2: 같은 거래는 어느 리포트에서 보든 같은 날, 같은 금액으로 잡혀야 한다
 * T-3: 취소된 예약은 매출에 포함되지 않아야 한다
 *
 * 인수 조건: docs/acceptance-criteria.md
 *
 * 격리: rental_records/sales_records/products/reservations는 다른 테이블이 FK로 참조하지
 * 않는(또는 우리가 쓰는 id 범위를 참조하지 않는) 테이블이라 deleteAll() 대신 자기 id 영역을
 * 쓸 수 있지만, data.sql이 각각 명시적 id(rental_records 1~6, sales_records 1~5, products
 * 1~12, reservations 1~12)로 시드 row를 넣어 둬 JPA GenerationType.IDENTITY 시퀀스가 이를
 * 인식하지 못한다(H2 확인됨, T-9 참고). ProductUpdateAcceptanceTest와 같은 방식으로 id 1000
 * 이상을 자기 데이터 영역으로 잡고 @Sql로 네 테이블 모두 시퀀스를 초기화한다.
 *
 * reservations는 생성 REST API가 없어(관리자용 시스템이라 조회·상태변경만 제공, T-8 참고)
 * ReservationRepository를 직접 사용해 만든다 — 상태 변경만 기존 PATCH 엔드포인트를 쓴다.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {
        "classpath:sql/reset-product-identity-sequence.sql",
        "classpath:sql/reset-rental-identity-sequence.sql",
        "classpath:sql/reset-sales-identity-sequence.sql",
        "classpath:sql/reset-reservation-identity-sequence.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = {
        "classpath:sql/delete-test-rentals.sql",
        "classpath:sql/delete-test-sales.sql",
        "classpath:sql/delete-test-products.sql",
        "classpath:sql/delete-test-reservations.sql"
}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RevenueReportAcceptanceTest {

    @LocalServerPort
    int port;

    String accessToken;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    CampsiteRepository campsiteRepository;

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
        log.info("\n\n======== setUp completed — [{}] ========\n", testInfo.getDisplayName());
    }

    private Long createProduct(String name, int stockQuantity, int price, String productType) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "%s",
                          "stockQuantity": %d,
                          "price": %d,
                          "productType": "%s"
                        }
                        """.formatted(name, stockQuantity, price, productType))
                .when().post("/admin/products")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void createWalkInRental(Long productId, int quantity) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "reservationId": null,
                          "productId": %d,
                          "quantity": %d
                        }
                        """.formatted(productId, quantity))
                .when().post("/admin/rentals")
                .then().statusCode(201);
    }

    private void createSale(Long productId, int quantity) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "items": [
                            {"productId": %d, "quantity": %d}
                          ]
                        }
                        """.formatted(productId, quantity))
                .when().post("/api/sales")
                .then().statusCode(200);
    }

    private void updateProductPrice(Long productId, int newPrice) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "price": %d
                        }
                        """.formatted(newPrice))
                .when().put("/admin/products/{id}", productId)
                .then().statusCode(200);
    }

    private float revenueEntryAmountByTitle(LocalDate date, String title) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", date.toString())
                .queryParam("to", date.toString())
                .when().get("/admin/reports/revenue/range/entries")
                .then().statusCode(200)
                .extract().jsonPath()
                .getFloat("find { it.title == '%s' }.amount".formatted(title));
    }

    private float dailyTotalRentalRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("date", date.toString())
                .when().get("/admin/reports/revenue/daily")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalRentalRevenue");
    }

    private float rangeTotalRentalRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", date.toString())
                .queryParam("to", date.toString())
                .when().get("/admin/reports/revenue/range")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalRentalRevenue");
    }

    private float dailyTotalSalesRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("date", date.toString())
                .when().get("/admin/reports/revenue/daily")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalSalesRevenue");
    }

    private float rangeTotalSalesRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", date.toString())
                .queryParam("to", date.toString())
                .when().get("/admin/reports/revenue/range")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalSalesRevenue");
    }

    private Long createOneNightReservationToday() {
        LocalDate today = LocalDate.now();
        Reservation reservation = new Reservation(
                "T3예약자",
                today,
                today.plusDays(1),
                campsiteRepository.findById(1L).orElseThrow());
        reservation.setReservationDate(today);
        return reservationRepository.save(reservation).getId();
    }

    private void cancelReservation(Long reservationId) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "status": "CANCELLED"
                        }
                        """)
                .when().patch("/admin/reservations/{id}/status", reservationId)
                .then().statusCode(200);
    }

    private float dailyTotalReservationRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("date", date.toString())
                .when().get("/admin/reports/revenue/daily")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalReservationRevenue");
    }

    private float rangeTotalReservationRevenue(LocalDate date) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", date.toString())
                .queryParam("to", date.toString())
                .when().get("/admin/reports/revenue/range")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("totalReservationRevenue");
    }

    private int rangeEntryCountForReservation(LocalDate date, Long reservationId) {
        List<Object> matches = given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", date.toString())
                .queryParam("to", date.toString())
                .when().get("/admin/reports/revenue/range/entries")
                .then().statusCode(200)
                .extract().jsonPath()
                .getList("findAll { it.title == '예약 #%d' }".formatted(reservationId));
        return matches.size();
    }

    @Nested
    @DisplayName("T-2: 대여(rental) 거래의 매출 금액은 조회 시점의 상품 가격이 아니라 거래가 발생한 시점의 금액으로 고정되어야 한다")
    class T2_대여_매출은_거래_시점_금액으로_고정되어야_한다 {

        @Nested
        @DisplayName("매출 상세내역 조회")
        class 매출_상세내역_조회 {

            @Test
            @DisplayName("대여 거래 완료 후 상품 가격이 바뀌어도 매출 금액은 거래 시점 금액을 유지해야 한다")
            void 대여_완료_후_가격이_바뀌어도_매출_금액은_거래_시점_금액을_유지해야_한다() {
                Long productId = createProduct("T2대여상품", 20, 30000, "RENTAL");
                createWalkInRental(productId, 1);

                LocalDate today = LocalDate.now();
                float amountBeforePriceChange = revenueEntryAmountByTitle(today, "T2대여상품");
                assertThat(amountBeforePriceChange).isEqualTo(30000f);

                updateProductPrice(productId, 99000);

                float amountAfterPriceChange = revenueEntryAmountByTitle(today, "T2대여상품");
                assertThat(amountAfterPriceChange).isEqualTo(30000f);
            }

            @Test
            @DisplayName("대여 거래 완료 후 상품 가격이 바뀌어도 일별 리포트에 반영된 이 거래의 대여 매출은 거래 시점 금액을 유지해야 한다")
            void 대여_완료_후_가격이_바뀌어도_일별_리포트에_반영된_이_거래의_대여_매출은_거래_시점_금액을_유지해야_한다() {
                LocalDate today = LocalDate.now();
                float baseline = dailyTotalRentalRevenue(today);

                Long productId = createProduct("T2일별대여상품", 20, 30000, "RENTAL");
                createWalkInRental(productId, 1);
                assertThat(dailyTotalRentalRevenue(today) - baseline).isEqualTo(30000f);

                updateProductPrice(productId, 99000);

                assertThat(dailyTotalRentalRevenue(today) - baseline).isEqualTo(30000f);
            }

            @Test
            @DisplayName("대여 거래 완료 후 상품 가격이 바뀌어도 기간 리포트에 반영된 이 거래의 대여 매출은 거래 시점 금액을 유지해야 한다")
            void 대여_완료_후_가격이_바뀌어도_기간_리포트에_반영된_이_거래의_대여_매출은_거래_시점_금액을_유지해야_한다() {
                LocalDate today = LocalDate.now();
                float baseline = rangeTotalRentalRevenue(today);

                Long productId = createProduct("T2기간대여상품", 20, 30000, "RENTAL");
                createWalkInRental(productId, 1);
                assertThat(rangeTotalRentalRevenue(today) - baseline).isEqualTo(30000f);

                updateProductPrice(productId, 99000);

                assertThat(rangeTotalRentalRevenue(today) - baseline).isEqualTo(30000f);
            }
        }
    }

    @Nested
    @DisplayName("T-2: 판매(sales) 거래의 매출 금액은 이후 상품 가격이 바뀌어도 거래 시점 금액 그대로 유지되어야 한다")
    class T2_판매_매출은_가격_변경과_무관하게_유지되어야_한다 {

        @Nested
        @DisplayName("매출 상세내역 조회")
        class 매출_상세내역_조회 {

            @Test
            @DisplayName("판매 거래 완료 후 상품 가격이 바뀌어도 매출 금액은 거래 시점 금액 그대로 유지된다")
            void 판매_완료_후_가격이_바뀌어도_매출_금액은_거래_시점_금액_그대로_유지된다() {
                Long productId = createProduct("T2판매상품", 50, 55555, "SALE");
                createSale(productId, 1);

                LocalDate today = LocalDate.now();
                float amountBeforePriceChange = revenueEntryAmountByTitle(today, "T2판매상품 외");
                assertThat(amountBeforePriceChange).isEqualTo(55555f);

                updateProductPrice(productId, 12345);

                float amountAfterPriceChange = revenueEntryAmountByTitle(today, "T2판매상품 외");
                assertThat(amountAfterPriceChange).isEqualTo(55555f);
            }

            @Test
            @DisplayName("판매 거래 완료 후 상품 가격이 바뀌어도 일별 리포트에 반영된 이 거래의 판매 매출은 거래 시점 금액 그대로 유지된다")
            void 판매_완료_후_가격이_바뀌어도_일별_리포트에_반영된_이_거래의_판매_매출은_거래_시점_금액_그대로_유지된다() {
                LocalDate today = LocalDate.now();
                float baseline = dailyTotalSalesRevenue(today);

                Long productId = createProduct("T2일별판매상품", 50, 55555, "SALE");
                createSale(productId, 1);
                assertThat(dailyTotalSalesRevenue(today) - baseline).isEqualTo(55555f);

                updateProductPrice(productId, 12345);

                assertThat(dailyTotalSalesRevenue(today) - baseline).isEqualTo(55555f);
            }

            @Test
            @DisplayName("판매 거래 완료 후 상품 가격이 바뀌어도 기간 리포트에 반영된 이 거래의 판매 매출은 거래 시점 금액 그대로 유지된다")
            void 판매_완료_후_가격이_바뀌어도_기간_리포트에_반영된_이_거래의_판매_매출은_거래_시점_금액_그대로_유지된다() {
                LocalDate today = LocalDate.now();
                float baseline = rangeTotalSalesRevenue(today);

                Long productId = createProduct("T2기간판매상품", 50, 55555, "SALE");
                createSale(productId, 1);
                assertThat(rangeTotalSalesRevenue(today) - baseline).isEqualTo(55555f);

                updateProductPrice(productId, 12345);

                assertThat(rangeTotalSalesRevenue(today) - baseline).isEqualTo(55555f);
            }
        }
    }

    @Nested
    @DisplayName("T-2: 같은 시점에 조회하면 일별 리포트·기간 리포트·기간 상세내역의 합계 금액이 서로 일치해야 한다")
    class T2_세_리포트의_합계_금액이_서로_일치해야_한다 {

        @Nested
        @DisplayName("리포트 조회")
        class 리포트_조회 {

            @Test
            @DisplayName("여러 날에 걸친 기간으로 조회해도 기간 리포트 합계는 일별 합계 총합·상세내역 합계와 일치한다")
            void 여러_날에_걸친_기간으로_조회해도_기간_리포트_합계는_일별_합계_총합_상세내역_합계와_일치한다() {
                // data.sql 시드 중 판매·대여가 모두 있는 -25일~-15일(10일 범위)로 실측한다.
                LocalDate from = LocalDate.now().minusDays(25);
                LocalDate to = LocalDate.now().minusDays(15);

                float rangeGrandTotal = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("grandTotalRevenue");

                List<Float> entryAmounts = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath().getList("amount", Float.class);
                float entriesSum = 0f;
                for (float amount : entryAmounts) {
                    entriesSum += amount;
                }

                float dailyTotalsSum = 0f;
                for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                    dailyTotalsSum += given()
                            .header("Authorization", "Bearer " + accessToken)
                            .queryParam("date", date.toString())
                            .when().get("/admin/reports/revenue/daily")
                            .then().statusCode(200)
                            .extract().jsonPath().getFloat("grandTotalRevenue");
                }

                assertThat(dailyTotalsSum).isEqualTo(rangeGrandTotal);
                assertThat(entriesSum).isEqualTo(rangeGrandTotal);
            }
        }
    }

    @Nested
    @DisplayName("T-2: 대여·판매 거래는 상품 가격이 나중에 바뀌어도 그 거래가 집계되는 날짜가 거래 발생 시점 그대로 유지되어야 한다")
    class T2_거래_집계_날짜는_가격_변경과_무관하게_유지되어야_한다 {

        @Nested
        @DisplayName("기간 상세내역 조회")
        class 기간_상세내역_조회 {

            @Test
            @DisplayName("상품 가격이 바뀌어도 대여·판매 거래의 상세내역 날짜와 금액은 거래 발생 시점 그대로 유지된다")
            void 상품_가격이_바뀌어도_대여_판매_거래의_상세내역_날짜와_금액은_거래_발생_시점_그대로_유지된다() {
                LocalDate today = LocalDate.now();

                Long rentalProductId = createProduct("T2날짜대여상품", 20, 30000, "RENTAL");
                createWalkInRental(rentalProductId, 1);
                createWalkInRental(rentalProductId, 1);
                Long saleProductId = createProduct("T2날짜판매상품", 50, 2500, "SALE");
                createSale(saleProductId, 1);

                updateProductPrice(rentalProductId, 99000);
                updateProductPrice(saleProductId, 9999);

                List<Float> rentalAmounts = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath()
                        .getList("findAll { it.title == 'T2날짜대여상품' }.amount", Float.class);
                List<String> rentalDates = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath()
                        .getList("findAll { it.title == 'T2날짜대여상품' }.occurredAt", String.class);
                List<Float> saleAmounts = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath()
                        .getList("findAll { it.title == 'T2날짜판매상품 외' }.amount", Float.class);
                List<String> saleDates = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath()
                        .getList("findAll { it.title == 'T2날짜판매상품 외' }.occurredAt", String.class);

                assertThat(rentalAmounts).containsExactlyInAnyOrder(30000f, 30000f);
                assertThat(rentalDates).allMatch(occurredAt -> occurredAt.startsWith(today.toString()));
                assertThat(saleAmounts).containsExactly(2500f);
                assertThat(saleDates).allMatch(occurredAt -> occurredAt.startsWith(today.toString()));
            }
        }

        @Nested
        @DisplayName("일별 리포트 조회")
        class 일별_리포트_조회 {

            @Test
            @DisplayName("상품 가격이 바뀌어도 일별 리포트에 반영된 이 거래들의 매출은 거래 발생 시점 금액 그대로 유지된다")
            void 상품_가격이_바뀌어도_일별_리포트에_반영된_이_거래들의_매출은_거래_발생_시점_금액_그대로_유지된다() {
                LocalDate today = LocalDate.now();

                float rentalBaseline = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("date", today.toString())
                        .when().get("/admin/reports/revenue/daily")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalRentalRevenue");
                float saleBaseline = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("date", today.toString())
                        .when().get("/admin/reports/revenue/daily")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalSalesRevenue");

                Long rentalProductId = createProduct("T2일별날짜대여상품", 20, 30000, "RENTAL");
                createWalkInRental(rentalProductId, 1);
                createWalkInRental(rentalProductId, 1);
                Long saleProductId = createProduct("T2일별날짜판매상품", 50, 2500, "SALE");
                createSale(saleProductId, 1);

                updateProductPrice(rentalProductId, 99000);
                updateProductPrice(saleProductId, 9999);

                float rentalRevenue = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("date", today.toString())
                        .when().get("/admin/reports/revenue/daily")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalRentalRevenue");
                float saleRevenue = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("date", today.toString())
                        .when().get("/admin/reports/revenue/daily")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalSalesRevenue");
                assertThat(rentalRevenue - rentalBaseline).isEqualTo(60000f);
                assertThat(saleRevenue - saleBaseline).isEqualTo(2500f);
            }
        }

        @Nested
        @DisplayName("기간 리포트 조회")
        class 기간_리포트_조회 {

            @Test
            @DisplayName("상품 가격이 바뀌어도 기간 리포트의 시작일·종료일과 이 거래들의 매출은 거래 발생 시점 그대로 유지된다")
            void 상품_가격이_바뀌어도_기간_리포트의_시작일_종료일과_이_거래들의_매출은_거래_발생_시점_그대로_유지된다() {
                LocalDate today = LocalDate.now();

                float rentalBaseline = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalRentalRevenue");
                float saleBaseline = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalSalesRevenue");

                Long rentalProductId = createProduct("T2기간날짜대여상품", 20, 30000, "RENTAL");
                createWalkInRental(rentalProductId, 1);
                createWalkInRental(rentalProductId, 1);
                Long saleProductId = createProduct("T2기간날짜판매상품", 50, 2500, "SALE");
                createSale(saleProductId, 1);

                updateProductPrice(rentalProductId, 99000);
                updateProductPrice(saleProductId, 9999);

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .body("fromDate", org.hamcrest.Matchers.equalTo(today.toString()))
                        .body("toDate", org.hamcrest.Matchers.equalTo(today.toString()));
                float rentalRevenue = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalRentalRevenue");
                float saleRevenue = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", today.toString())
                        .queryParam("to", today.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("totalSalesRevenue");
                assertThat(rentalRevenue - rentalBaseline).isEqualTo(60000f);
                assertThat(saleRevenue - saleBaseline).isEqualTo(2500f);
            }
        }
    }

    @Nested
    @DisplayName("T-3: 취소된 예약은 일별 리포트·기간 리포트·기간 상세내역 어디에서도 매출로 집계되지 않아야 한다")
    class T3_취소된_예약은_매출에서_제외되어야_한다 {

        @Nested
        @DisplayName("예약 취소")
        class 예약_취소 {

            @Test
            @DisplayName("예약을 취소하면 일별 리포트의 예약 매출에서 제외된다")
            void 예약을_취소하면_일별_리포트의_예약_매출에서_제외된다() {
                // given
                LocalDate today = LocalDate.now();
                float baseline = dailyTotalReservationRevenue(today);

                Long reservationId = createOneNightReservationToday();
                assertThat(dailyTotalReservationRevenue(today) - baseline).isEqualTo(50000f);

                // when
                cancelReservation(reservationId);

                //then
                assertThat(dailyTotalReservationRevenue(today) - baseline).isEqualTo(0f);
            }

            @Test
            @DisplayName("예약을 취소하면 기간 리포트의 예약 매출에서 제외된다")
            void 예약을_취소하면_기간_리포트의_예약_매출에서_제외된다() {
                // given
                LocalDate today = LocalDate.now();
                float baseline = rangeTotalReservationRevenue(today);

                Long reservationId = createOneNightReservationToday();
                assertThat(rangeTotalReservationRevenue(today) - baseline).isEqualTo(50000f);

                // when
                cancelReservation(reservationId);

                // then
                assertThat(rangeTotalReservationRevenue(today) - baseline).isEqualTo(0f);
            }

            @Test
            @DisplayName("예약을 취소하면 기간 상세내역 목록에서 제외된다")
            void 예약을_취소하면_기간_상세내역_목록에서_제외된다() {
                // given
                LocalDate today = LocalDate.now();

                Long reservationId = createOneNightReservationToday();
                assertThat(rangeEntryCountForReservation(today, reservationId)).isEqualTo(1);

                // when
                cancelReservation(reservationId);

                // then
                assertThat(rangeEntryCountForReservation(today, reservationId)).isEqualTo(0);
            }
        }
    }
}
