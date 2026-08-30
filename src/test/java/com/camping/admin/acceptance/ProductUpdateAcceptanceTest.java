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
    @DisplayName("T-1: 상품 정보를 수정하면(이름·재고·가격·유형 중 무엇을 바꾸든) 그 값이 저장되어야 한다")
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

    @Nested
    @DisplayName("T-1: 상품 정보 중 일부만 수정하면, 요청에 없는 필드는 원래 값 그대로 유지되어야 한다")
    class T1_부분_수정_시_요청에_없는_필드는_그대로_유지되어야_한다 {

        @Nested
        @DisplayName("상품 수정")
        class 상품_수정 {

            @Test
            @DisplayName("이름만 수정하면 재고와 가격은 그대로 유지된다")
            void 이름만_수정하면_재고와_가격은_그대로_유지된다() {
                Long productId = createProduct("테스트랜턴", 20, 30000, "RENTAL");

                var response = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": "새랜턴"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .extract();

                assertThat(response.jsonPath().getInt("stockQuantity")).isEqualTo(20);
                assertThat(response.jsonPath().getFloat("price")).isEqualTo(30000f);
                assertThat(response.jsonPath().getString("productType")).isEqualTo("RENTAL");
            }
        }
    }

    @Nested
    @DisplayName("T-5: 상품 정보를 수정할 때 요청에 포함된 값이 무효하면 그 필드를 반영하지 않고 400을 반환해야 한다")
    class T5_상품_수정_시_무효한_값은_거부되어야_한다 {

        @Nested
        @DisplayName("상품 수정")
        class 상품_수정 {

            @Test
            @DisplayName("재고를 음수로 수정하면 거부되고 재고는 그대로 유지된다")
            void 재고를_음수로_수정하면_거부되고_재고는_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": -1
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("stockQuantity는 0 이상이어야 합니다"));

                int reloadedStockQuantity = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId));

                assertThat(reloadedStockQuantity).isEqualTo(20);
            }

            @Test
            @DisplayName("재고를 0으로 수정하면 반영된다")
            void 재고를_0으로_수정하면_반영된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": 0
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("stockQuantity", equalTo(0));
            }

            @Test
            @DisplayName("가격을 음수로 수정하면 거부되고 가격은 그대로 유지된다")
            void 가격을_음수로_수정하면_거부되고_가격은_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": -1
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("price는 0 이상이어야 합니다"));

                float reloadedPrice = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("find { it.id == %d }.price".formatted(productId));

                assertThat(reloadedPrice).isEqualTo(30000f);
            }

            @Test
            @DisplayName("가격을 0으로 수정하면 반영된다")
            void 가격을_0으로_수정하면_반영된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": 0
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("price", equalTo(0));
            }

            @Test
            @DisplayName("이름을 빈 문자열로 수정하면 거부되고 이름은 그대로 유지된다")
            void 이름을_빈_문자열로_수정하면_거부되고_이름은_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": ""
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("name은 빈 문자열일 수 없습니다"));

                String reloadedName = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getString("find { it.id == %d }.name".formatted(productId));

                assertThat(reloadedName).isEqualTo("T5검증상품");
            }

            @Test
            @DisplayName("정의되지 않은 유형으로 수정하면 거부되고 유형은 그대로 유지된다")
            void 정의되지_않은_유형으로_수정하면_거부되고_유형은_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "productType": "FOO"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("productType은 정의된 값이어야 합니다"));

                String reloadedProductType = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getString("find { it.id == %d }.productType".formatted(productId));

                assertThat(reloadedProductType).isEqualTo("RENTAL");
            }

            @Test
            @DisplayName("숫자로 파싱할 수 없는 재고 값으로 수정하면 거부되고 재고는 그대로 유지된다")
            void 숫자로_파싱할_수_없는_재고_값으로_수정하면_거부되고_재고는_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": "abc"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("stockQuantity는 숫자여야 합니다"));

                int reloadedStockQuantity = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId));

                assertThat(reloadedStockQuantity).isEqualTo(20);
            }

            @Test
            @DisplayName("숫자로 파싱할 수 없는 가격 값으로 수정하면 거부되고 가격은 그대로 유지된다")
            void 숫자로_파싱할_수_없는_가격_값으로_수정하면_거부되고_가격은_그대로_유지된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": "abc"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("price는 숫자여야 합니다"));

                float reloadedPrice = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("find { it.id == %d }.price".formatted(productId));

                assertThat(reloadedPrice).isEqualTo(30000f);
            }
        }
    }

    @Nested
    @DisplayName("T-5: 한 요청에 유효한 필드와 무효한 필드가 함께 있으면, 유효한 필드도 반영하지 않고 요청 전체를 거부해야 한다")
    class T5_유효한_필드와_무효한_필드가_함께_있으면_전체_거부되어야_한다 {

        @Nested
        @DisplayName("상품 수정")
        class 상품_수정 {

            @Test
            @DisplayName("유효한 이름과 무효한 재고를 함께 수정하면 이름도 반영되지 않는다")
            void 유효한_이름과_무효한_재고를_함께_수정하면_이름도_반영되지_않는다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": "새이름",
                                  "stockQuantity": -5
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("stockQuantity는 0 이상이어야 합니다"));

                var reloaded = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract();

                assertThat(reloaded.jsonPath().getString("find { it.id == %d }.name".formatted(productId)))
                        .isEqualTo("T5검증상품");
                assertThat(reloaded.jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId)))
                        .isEqualTo(20);
            }

            @Test
            @DisplayName("모든 필드를 유효한 값으로 함께 수정하면 반영된다")
            void 모든_필드를_유효한_값으로_함께_수정하면_반영된다() {
                Long productId = createProduct("T5검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": "업데이트랜턴",
                                  "stockQuantity": 50,
                                  "price": 45000,
                                  "productType": "SALE"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("name", equalTo("업데이트랜턴"))
                        .body("stockQuantity", equalTo(50))
                        .body("productType", equalTo("SALE"));
            }
        }
    }

    @Nested
    @DisplayName("T-5: 재고·가격이 문자열 형태의 음수로 전달되더라도 숫자로 전달했을 때와 동일하게 거부해야 한다")
    class T5_문자열_형태의_음수_재고_가격도_거부되어야_한다 {

        @Nested
        @DisplayName("상품 수정")
        class 상품_수정 {

            @Test
            @DisplayName("재고를 문자열 음수로 수정하면 거부되고 재고는 그대로 유지된다")
            void 재고를_문자열_음수로_수정하면_거부되고_재고는_그대로_유지된다() {
                Long productId = createProduct("T5문자열검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": "-1"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("stockQuantity는 0 이상이어야 합니다"));

                int reloadedStockQuantity = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId));

                assertThat(reloadedStockQuantity).isEqualTo(20);
            }

            @Test
            @DisplayName("재고를 문자열 \"0\"으로 수정하면 반영된다")
            void 재고를_문자열_0으로_수정하면_반영된다() {
                Long productId = createProduct("T5문자열검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "stockQuantity": "0"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("stockQuantity", equalTo(0));
            }

            @Test
            @DisplayName("가격을 문자열 음수로 수정하면 거부되고 가격은 그대로 유지된다")
            void 가격을_문자열_음수로_수정하면_거부되고_가격은_그대로_유지된다() {
                Long productId = createProduct("T5문자열검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": "-1"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("price는 0 이상이어야 합니다"));

                float reloadedPrice = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract().jsonPath().getFloat("find { it.id == %d }.price".formatted(productId));

                assertThat(reloadedPrice).isEqualTo(30000f);
            }

            @Test
            @DisplayName("가격을 문자열 \"0\"으로 수정하면 반영된다")
            void 가격을_문자열_0으로_수정하면_반영된다() {
                Long productId = createProduct("T5문자열검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "price": "0"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(200)
                        .body("price", equalTo(0));
            }

            @Test
            @DisplayName("유효한 이름과 문자열 형태의 무효한 재고를 함께 수정하면 이름도 반영되지 않는다")
            void 유효한_이름과_문자열_형태의_무효한_재고를_함께_수정하면_이름도_반영되지_않는다() {
                Long productId = createProduct("T5문자열검증상품", 20, 30000, "RENTAL");

                given()
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "name": "새이름2",
                                  "stockQuantity": "-5"
                                }
                                """)
                        .when().put("/admin/products/{id}", productId)
                        .then().statusCode(400)
                        .body("error", equalTo("stockQuantity는 0 이상이어야 합니다"));

                var reloaded = given()
                        .header("Authorization", "Bearer " + accessToken)
                        .when().get("/admin/products")
                        .then().statusCode(200)
                        .extract();

                assertThat(reloaded.jsonPath().getString("find { it.id == %d }.name".formatted(productId)))
                        .isEqualTo("T5문자열검증상품");
                assertThat(reloaded.jsonPath().getInt("find { it.id == %d }.stockQuantity".formatted(productId)))
                        .isEqualTo(20);
            }
        }
    }
}
