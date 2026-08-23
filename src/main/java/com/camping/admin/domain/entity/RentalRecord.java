package com.camping.admin.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "rental_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id") // Nullable for walk-in rentals
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "is_returned", nullable = false)
    private Boolean isReturned = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public RentalRecord(Reservation reservation, Product product, Integer quantity, BigDecimal totalPrice) {
        this.reservation = reservation;
        this.product = product;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.isReturned = false;
        this.createdAt = LocalDateTime.now();
    }

    public void setReturned(Boolean returned) {
        isReturned = returned;
    }
}
