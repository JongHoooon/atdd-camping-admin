package com.camping.admin.service;

import com.camping.admin.domain.entity.Product;
import com.camping.admin.domain.entity.RentalRecord;
import com.camping.admin.domain.entity.Reservation;
import com.camping.admin.domain.enums.ProductType;
import com.camping.admin.dto.RentalResponse;
import com.camping.admin.repository.ProductRepository;
import com.camping.admin.repository.RentalRecordRepository;
import com.camping.admin.repository.ReservationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalService {

    private final RentalRecordRepository rentalRecordRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ProductService productService;

    public List<RentalResponse> findAll() {
        return rentalRecordRepository.findAll().stream()
                .map(RentalResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public RentalResponse createRental(Long productId, Integer quantity, Long reservationId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find product with id: " + productId));

        if (product.getProductType() != ProductType.RENTAL) {
            throw new IllegalArgumentException("Product is not a rental item.");
        }

        productService.decreaseStock(productId, quantity);

        Reservation reservation = null;
        if (reservationId != null) {
            reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot find reservation with id: " + reservationId));
        }

        BigDecimal totalPrice = product.getPrice().multiply(new BigDecimal(quantity));
        RentalRecord rentalRecord = new RentalRecord(reservation, product, quantity, totalPrice);
        RentalRecord savedRentalRecord = rentalRecordRepository.save(rentalRecord);

        return RentalResponse.from(savedRentalRecord);
    }

    @Transactional
    public RentalResponse markAsReturned(Long rentalRecordId) {
        RentalRecord rentalRecord = rentalRecordRepository.findById(rentalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find rental record with id: " + rentalRecordId));
        
        if (rentalRecord.getIsReturned()) {
            throw new IllegalStateException("This item has already been returned.");
        }
        rentalRecord.setReturned(true);
        
        productService.increaseStock(rentalRecord.getProduct().getId(), rentalRecord.getQuantity());
        
        return RentalResponse.from(rentalRecord);
    }
}