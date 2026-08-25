package com.camping.admin.service;

import com.camping.admin.domain.entity.Product;
import com.camping.admin.domain.entity.RentalRecord;
import com.camping.admin.domain.entity.SalesRecord;
import com.camping.admin.dto.DailyRevenueReportResponse;
import com.camping.admin.dto.RangeRevenueReportResponse;
import com.camping.admin.dto.ProcessSaleRequest;
import com.camping.admin.dto.RevenueEntryResponse;
import com.camping.admin.dto.SaleItemResponse;
import com.camping.admin.dto.SalesRecordResponse;
import com.camping.admin.repository.ProductRepository;
import com.camping.admin.repository.RentalRecordRepository;
import com.camping.admin.repository.ReservationRepository;
import com.camping.admin.repository.SalesRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

    private final ProductRepository productRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final ReservationRepository reservationRepository; // For reports
    private final RentalRecordRepository rentalRecordRepository; // For reports
    private final ProductService productService; // To use decreaseStock

    @Transactional
    public void processSale(ProcessSaleRequest request) {
        for (SaleItemResponse itemDto : request.getItems()) {
            productService.decreaseStock(itemDto.getProductId(), itemDto.getQuantity());

            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Cannot find product with id: " + itemDto.getProductId()));
            
            BigDecimal totalPrice = product.getPrice().multiply(new BigDecimal(itemDto.getQuantity()));
            
            SalesRecord salesRecord = new SalesRecord(product, itemDto.getQuantity(), totalPrice);
            salesRecordRepository.save(salesRecord);
        }
    }

    public List<SalesRecordResponse> findRecentSales(int limit) {
        return salesRecordRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(com.camping.admin.domain.entity.SalesRecord::getCreatedAt).reversed())
                .limit(limit)
                .map(SalesRecordResponse::from)
                .toList();
    }

    public DailyRevenueReportResponse generateDailyRevenueReport(LocalDate date) {
        BigDecimal totalSalesRevenue = salesRecordRepository.findAll().stream()
                .filter(record -> record.getCreatedAt().toLocalDate().equals(date))
                .map(SalesRecord::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReservationRevenue = reservationRepository.findAll().stream()
                .filter(r -> r.getReservationDate() != null && r.getReservationDate().equals(date))
                .filter(r -> !"CANCELLED".equals(r.getStatus()))
                .map(r -> {
                    long nights = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate());
                    if (nights < 1) nights = 1; // 최소 1박 처리
                    return new BigDecimal(nights).multiply(new BigDecimal("50000"));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRentalRevenue = rentalRecordRepository.findAll().stream()
                .filter(rr -> rr.getCreatedAt().toLocalDate().equals(date))
                .map(RentalRecord::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotal = totalSalesRevenue.add(totalReservationRevenue).add(totalRentalRevenue);

        return new DailyRevenueReportResponse(date, totalReservationRevenue, totalSalesRevenue, totalRentalRevenue, grandTotal);
    }

    public RangeRevenueReportResponse generateRangeRevenueReport(LocalDate from, LocalDate to) {
        BigDecimal totalSalesRevenue = salesRecordRepository.findAll().stream()
                .filter(record -> {
                    LocalDate d = record.getCreatedAt().toLocalDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .map(SalesRecord::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReservationRevenue = reservationRepository.findAll().stream()
                .filter(r -> r.getReservationDate() != null)
                .filter(r -> {
                    LocalDate d = r.getReservationDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .filter(r -> !"CANCELLED".equals(r.getStatus()))
                .map(r -> {
                    long nights = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate());
                    if (nights < 1) nights = 1;
                    return new BigDecimal(nights).multiply(new BigDecimal("50000"));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRentalRevenue = rentalRecordRepository.findAll().stream()
                .filter(rr -> {
                    LocalDate d = rr.getCreatedAt().toLocalDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .map(RentalRecord::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotal = totalSalesRevenue.add(totalReservationRevenue).add(totalRentalRevenue);

        return new RangeRevenueReportResponse(from, to, totalReservationRevenue, totalSalesRevenue, totalRentalRevenue, grandTotal);
    }

    public List<RevenueEntryResponse> generateRangeRevenueEntries(LocalDate from, LocalDate to) {
        List<RevenueEntryResponse> entries = new ArrayList<>();

        salesRecordRepository.findAll().stream()
                .filter(record -> {
                    LocalDate d = record.getCreatedAt().toLocalDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .forEach(record -> entries.add(new RevenueEntryResponse(
                        RevenueEntryResponse.EntryType.SALE,
                        record.getProduct().getName() + " 외",
                        record.getTotalPrice(),
                        record.getCreatedAt()
                )));

        reservationRepository.findAll().stream()
                .filter(r -> r.getReservationDate() != null)
                .filter(r -> {
                    LocalDate d = r.getReservationDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .filter(r -> !"CANCELLED".equals(r.getStatus()))
                .forEach(r -> {
                    long nights = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate());
                    if (nights < 1) nights = 1;
                    entries.add(new RevenueEntryResponse(
                            RevenueEntryResponse.EntryType.RESERVATION,
                            "예약 #" + r.getId(),
                            new java.math.BigDecimal(nights).multiply(new java.math.BigDecimal("50000")),
                            r.getCreatedAt()
                    ));
                });

        rentalRecordRepository.findAll().stream()
                .filter(rr -> {
                    LocalDate d = rr.getCreatedAt().toLocalDate();
                    return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
                })
                .forEach(rr -> entries.add(new RevenueEntryResponse(
                        RevenueEntryResponse.EntryType.RENTAL,
                        rr.getProduct().getName() + (rr.getReservation() != null ? " (예약#" + rr.getReservation().getId() + ")" : ""),
                        rr.getTotalPrice(),
                        rr.getCreatedAt()
                )));

        entries.sort(Comparator.comparing(RevenueEntryResponse::getOccurredAt));
        return entries;
    }
}