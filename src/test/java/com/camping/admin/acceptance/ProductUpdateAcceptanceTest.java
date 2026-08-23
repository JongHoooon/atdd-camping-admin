package com.camping.admin.acceptance;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * T-1: 상품 정보를 수정하면 그 값이 DB에 저장되어야 한다
 *
 * 인수 조건: docs/acceptance-criteria.md
 *
 * 격리: 실제 서버로 호출하므로 상태가 남는다. products는 sales_records/rental_records가
 * FK로 참조해 deleteAll()을 쓸 수 없으므로, 이 테스트는 id 1000 이상을 자기 데이터 영역으로
 * 잡는다. data.sql이 id=1~12를 명시적으로 미리 넣어 둬 JPA GenerationType.IDENTITY 시퀀스가
 * 이를 인식하지 못하므로(H2 확인됨), @Sql로 매 테스트 전 시퀀스를 1000으로 되돌리고 테스트
 * 후 1000 이상 row를 지워 다음 테스트가 항상 같은 조건에서 시작하게 한다.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "classpath:sql/reset-product-identity-sequence.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:sql/delete-test-products.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductUpdateAcceptanceTest {

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

    @Nested
    @DisplayName("T-1: PUT /admin/products/{id} 요청으로 name, stockQuantity, price, productType 중 어느 필드를 수정하든 그 값이 DB에 저장되어야 한다")
    class T1_상품_수정_필드값이_DB에_저장되어야_한다 {

        @Nested
        @DisplayName("상품 수정")
        class 상품_수정 {

            @Test
            @DisplayName("재고를 수정하면 재조회 시 변경된 재고가 반영된다")
            void 재고를_수정하면_재조회_시_변경된_재고가_반영된다() {
                Long productId = createProduct("테스트랜턴", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": 99
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("stockQuantity", equalTo(99));

                int reloadedStockQuantity = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId));

                assertThat(reloadedStockQuantity).isEqualTo(99);
            }

            @Test
            @DisplayName("이름을 수정하면 재조회 시 변경된 이름이 반영된다")
            void 이름을_수정하면_재조회_시_변경된_이름이_반영된다() {
                Long productId = createProduct("테스트랜턴", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": "새랜턴"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("name", equalTo("새랜턴"));

                String reloadedName = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getString("find { it.id == %d }.name".formatted(productId));

                assertThat(reloadedName).isEqualTo("새랜턴");
            }

            @Test
            @DisplayName("가격을 수정하면 재조회 시 변경된 가격이 반영된다")
            void 가격을_수정하면_재조회_시_변경된_가격이_반영된다() {
                Long productId = createProduct("테스트랜턴", 20, 30000, "RENTAL");

                float updatedPrice = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": 45000
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("price");

                assertThat(updatedPrice).isEqualTo(45000f);

                float reloadedPrice = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("find { it.id == %d }.price".formatted(productId));

                assertThat(reloadedPrice).isEqualTo(45000f);
            }

            @Test
            @DisplayName("유형을 수정하면 재조회 시 변경된 유형이 반영된다")
            void 유형을_수정하면_재조회_시_변경된_유형이_반영된다() {
                Long productId = createProduct("테스트랜턴", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "productType": "SALE"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("productType", equalTo("SALE"));

                String reloadedProductType = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getString("find { it.id == %d }.productType".formatted(productId));

                assertThat(reloadedProductType).isEqualTo("SALE");
            }
        }
    }
}
