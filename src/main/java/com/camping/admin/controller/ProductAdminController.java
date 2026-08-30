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
            if (body.containsKey("stockQuantity")) {
                Object v = body.get("stockQuantity");
                if (v instanceof Number) {
                    if (((Number) v).intValue() < 0) {
                        return badRequest("stockQuantity는 0 이상이어야 합니다");
                    }
                } else if (v != null) {
                    try {
                        if (Integer.valueOf(v.toString()) < 0) {
                            return badRequest("stockQuantity는 0 이상이어야 합니다");
                        }
                    } catch (NumberFormatException e) {
                        return badRequest("stockQuantity는 숫자여야 합니다");
                    }
                }
            }
            if (body.containsKey("price")) {
                Object v = body.get("price");
                if (v instanceof Number) {
                    if (new BigDecimal(v.toString()).compareTo(BigDecimal.ZERO) < 0) {
                        return badRequest("price는 0 이상이어야 합니다");
                    }
                } else if (v != null) {
                    try {
                        if (new BigDecimal(v.toString()).compareTo(BigDecimal.ZERO) < 0) {
                            return badRequest("price는 0 이상이어야 합니다");
                        }
                    } catch (NumberFormatException e) {
                        return badRequest("price는 숫자여야 합니다");
                    }
                }
            }
            if (body.containsKey("name")) {
                Object v = body.get("name");
                if (v != null && v.toString().isEmpty()) {
                    return badRequest("name은 빈 문자열일 수 없습니다");
                }
            }
            if (body.containsKey("productType")) {
                Object v = body.get("productType");
                if (v != null) {
                    try {
                        ProductType.valueOf(v.toString());
                    } catch (Exception e) {
                        return badRequest("productType은 정의된 값이어야 합니다");
                    }
                }
            }

            if (body.containsKey("name")) {
                Object v = body.get("name");
                if (v != null) {
                    product.setName(v.toString());
                }
            }
            if (body.containsKey("stockQuantity")) {
                Object v = body.get("stockQuantity");
                if (v instanceof Number) {
                    product.setStockQuantity(((Number) v).intValue());
                } else if (v != null) {
                    try {
                        product.setStockQuantity(Integer.valueOf(v.toString()));
                    } catch (Exception ignore) {
                    }
                }
            }
            if (body.containsKey("price")) {
                Object v = body.get("price");
                if (v instanceof Number) {
                    product.setPrice(new BigDecimal(((Number) v).toString()));
                } else if (v != null) {
                    try {
                        product.setPrice(new BigDecimal(v.toString()));
                    } catch (Exception ignore) {
                    }
                }
            }
            if (body.containsKey("productType")) {
                Object v = body.get("productType");
                if (v != null) {
                    try {
                        product.setProductType(ProductType.valueOf(v.toString()));
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}