package com.camping.admin.acceptance;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-2: 같은 거래는 어느 리포트에서 보든 같은 날, 같은 금액으로 잡혀야 한다
 *
 * 인수 조건: docs/acceptance-criteria.md
 *
 * 격리: rental_records/sales_records/products는 다른 테이블이 FK로 참조하지 않는 자식
 * 테이블이라 deleteAll()을 쓸 수 있지만, data.sql이 각각 명시적 id(rental_records 1~6,
 * sales_records 1~5, products 1~12)로 시드 row를 넣어 둬 JPA GenerationType.IDENTITY 시퀀스가
 * 이를 인식하지 못한다(H2 확인됨, T-9 참고). ProductUpdateAcceptanceTest와 같은 방식으로
 * id 1000 이상을 자기 데이터 영역으로 잡고 @Sql로 세 테이블 모두 시퀀스를 초기화한다.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {
        "classpath:sql/reset-product-identity-sequence.sql",
        "classpath:sql/reset-rental-identity-sequence.sql",
        "classpath:sql/reset-sales-identity-sequence.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = {
        "classpath:sql/delete-test-rentals.sql",
        "classpath:sql/delete-test-sales.sql",
        "classpath:sql/delete-test-products.sql"
}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RevenueReportAcceptanceTest {

    @LocalServerPort
    int port;

    String accessToken;

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

            @ParameterizedTest
            @ValueSource(ints = {0, 15, 25})
            @DisplayName("같은 시점에 조회하면 일별 리포트와 기간 리포트와 상세내역 합계 금액이 서로 일치한다")
            void 같은_시점에_조회하면_일별_리포트와_기간_리포트와_상세내역_합계_금액이_서로_일치한다(int daysAgo) {
                LocalDate targetDate = LocalDate.now().minusDays(daysAgo);

                if (daysAgo == 0) {
                    // 대여/판매는 생성 시점(now)으로만 찍혀 다른 날짜 데이터를 API로 만들 수 없다.
                    // -15일/-25일은 data.sql 시드에 판매·대여가 모두 있는 날짜라 그대로 실측한다.
                    Long saleProductId = createProduct("T2합계판매상품", 30, 20000, "SALE");
                    createSale(saleProductId, 1);
                    Long rentalProductId = createProduct("T2합계대여상품", 10, 40000, "RENTAL");
                    createWalkInRental(rentalProductId, 1);
                }

                float dailyGrandTotal = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("date", targetDate.toString())
                        .when().get("/admin/reports/revenue/daily")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("grandTotalRevenue");

                float rangeGrandTotal = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", targetDate.toString())
                        .queryParam("to", targetDate.toString())
                        .when().get("/admin/reports/revenue/range")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("grandTotalRevenue");

                List<Float> entryAmounts = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", targetDate.toString())
                        .queryParam("to", targetDate.toString())
                        .when().get("/admin/reports/revenue/range/entries")
                        .then().statusCode(200)
                        .extract().jsonPath().getList("amount", Float.class);
                float entriesSum = 0f;
                for (float amount : entryAmounts) {
                    entriesSum += amount;
                }

                assertThat(dailyGrandTotal).isEqualTo(rangeGrandTotal);
                assertThat(entriesSum).isEqualTo(rangeGrandTotal);
            }
        }
    }
}
