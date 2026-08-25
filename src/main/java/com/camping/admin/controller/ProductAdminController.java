package com.camping.admin.controller;

import com.camping.admin.domain.entity.Product;
import com.camping.admin.domain.enums.ProductType;
import com.camping.admin.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> all = productRepository.findAll();
        List<Product> result = new ArrayList<>();
        if (all != null) {
            for (Product p : all) {
                if (p != null) {
                    result.add(p);
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Map<String, Object> body) {
        String name;
        if (body.containsKey("name")) {
            Object v = body.get("name");
            name = v == null ? null : v.toString();
        } else {
            name = null;
        }

        Integer stockQuantity;
        if (body.containsKey("stockQuantity")) {
            Object v = body.get("stockQuantity");
            if (v instanceof Number) {
                stockQuantity = ((Number) v).intValue();
            } else if (v == null) {
                stockQuantity = 0;
            } else {
                try {
                    stockQuantity = Integer.valueOf(v.toString());
                } catch (Exception e) {
                    stockQuantity = 0;
                }
            }
        } else {
            stockQuantity = 0;
        }

        BigDecimal price;
        if (body.containsKey("price")) {
            Object v = body.get("price");
            if (v instanceof Number) {
                price = new BigDecimal(((Number) v).toString());
            } else if (v == null) {
                price = BigDecimal.ZERO;
            } else {
                try {
                    price = new BigDecimal(v.toString());
                } catch (Exception e) {
                    price = BigDecimal.ZERO;
                }
            }
        } else {
            price = BigDecimal.ZERO;
        }

        ProductType productType;
        if (body.containsKey("productType")) {
            Object v = body.get("productType");
            if (v == null) {
                productType = ProductType.SALE;
            } else {
                try {
                    productType = ProductType.valueOf(v.toString());
                } catch (Exception e) {
                    productType = ProductType.SALE;
                }
            }
        } else {
            productType = ProductType.SALE;
        }

        Product newProduct = new Product(name, stockQuantity, price, productType);
        Product saved = productRepository.save(newProduct);
        if (saved == null) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find product with id: " + productId));

        if (body != null) {
            if (body.containsKey("name")) {
                Object v = body.get("name");
                if (v != null) {
                    String name = v.toString();
                    if (name.isBlank()) {
                        return badRequest("상품명은 공백이거나 빈 문자열일 수 없습니다.");
                    }
                    product.setName(name);
                }
            }
            if (body.containsKey("stockQuantity")) {
                Object v = body.get("stockQuantity");
                if (v instanceof Number) {
                    int newStockQuantity = ((Number) v).intValue();
                    if (newStockQuantity < 0) {
                        return badRequest("재고 수량은 음수일 수 없습니다.");
                    }
                    product.setStockQuantity(newStockQuantity);
                } else if (v != null) {
                    try {
                        int newStockQuantity = Integer.valueOf(v.toString());
                        if (newStockQuantity < 0) {
                            return badRequest("재고 수량은 음수일 수 없습니다.");
                        }
                        product.setStockQuantity(newStockQuantity);
                    } catch (NumberFormatException e) {
                        return badRequest("재고 수량은 숫자여야 합니다: " + v);
                    }
                }
            }
            if (body.containsKey("price")) {
                Object v = body.get("price");
                if (v instanceof Number) {
                    BigDecimal newPrice = new BigDecimal(((Number) v).toString());
                    if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                        return badRequest("가격은 음수일 수 없습니다.");
                    }
                    product.setPrice(newPrice);
                } else if (v != null) {
                    try {
                        BigDecimal newPrice = new BigDecimal(v.toString());
                        if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                            return badRequest("가격은 음수일 수 없습니다.");
                        }
                        product.setPrice(newPrice);
                    } catch (NumberFormatException e) {
                        return badRequest("가격은 숫자여야 합니다: " + v);
                    }
                }
            }
            if (body.containsKey("productType")) {
                Object v = body.get("productType");
                if (v != null) {
                    try {
                        product.setProductType(ProductType.valueOf(v.toString()));
                    } catch (IllegalArgumentException e) {
                        return badRequest("정의되지 않은 상품 유형입니다: " + v);
                    }
                }
            }
        }

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return new ResponseEntity<>(Map.of("error", message), HttpStatus.BAD_REQUEST);
    }
}