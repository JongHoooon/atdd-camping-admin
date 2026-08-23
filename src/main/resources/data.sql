-- Campsites
insert into campsites (id, site_number, description, max_people) values
  (1, 'A-01', '숲 뷰, 전기가능', 4),
  (2, 'A-02', '강가, 그늘많음', 6);

-- Products
insert into products (id, name, stock_quantity, price, product_type) values
  (1, '랜턴', 20, 30000.00, 'RENTAL'),
  (2, '장작팩', 50, 10000.00, 'SALE'),
  -- 추가 RENTAL 5개
  (3, '코펠 세트', 15, 20000.00, 'RENTAL'),
  (4, '의자', 25, 15000.00, 'RENTAL'),
  (5, '테이블', 10, 25000.00, 'RENTAL'),
  (6, '버너', 12, 18000.00, 'RENTAL'),
  (7, '취사도구 세트', 30, 12000.00, 'RENTAL'),
  -- 추가 SALE 5개
  (8, '생수(2L)', 100, 2000.00, 'SALE'),
  (9, '라면 세트', 80, 4000.00, 'SALE'),
  (10, '스낵팩', 60, 3000.00, 'SALE'),
  (11, '휴지', 70, 2500.00, 'SALE'),
  (12, '아이스팩', 90, 1500.00, 'SALE');

-- Reservations
insert into reservations (id, customer_name, start_date, end_date, campsite_id, phone_number, status, reservation_date, confirmation_code, created_at)
values
  (1, '홍길동', current_date, current_date + 1, 1, '010-1111-2222', 'CONFIRMED', current_date, 'ABC123', current_timestamp),
  (2, '김철수', current_date + 1, current_date + 2, 2, '010-3333-4444', 'CONFIRMED', current_date, 'XYZ789', current_timestamp),
  -- 최근 한달 예약 더미 데이터
  (3, '이영희', DATEADD('DAY', -28, current_date), DATEADD('DAY', -27, current_date), 1, '010-5555-6666', 'CONFIRMED', DATEADD('DAY', -29, current_date), 'R00003', DATEADD('DAY', -29, current_timestamp)),
  (4, '박민수', DATEADD('DAY', -25, current_date), DATEADD('DAY', -24, current_date), 2, '010-7777-8888', 'CONFIRMED', DATEADD('DAY', -26, current_date), 'R00004', DATEADD('DAY', -26, current_timestamp)),
  (5, '최수정', DATEADD('DAY', -21, current_date), DATEADD('DAY', -19, current_date), 1, '010-9999-0000', 'CONFIRMED', DATEADD('DAY', -22, current_date), 'R00005', DATEADD('DAY', -22, current_timestamp)),
  (6, '정하늘', DATEADD('DAY', -18, current_date), DATEADD('DAY', -17, current_date), 2, '010-2222-3333', 'CONFIRMED', DATEADD('DAY', -19, current_date), 'R00006', DATEADD('DAY', -19, current_timestamp)),
  (7, '오세훈', DATEADD('DAY', -15, current_date), DATEADD('DAY', -14, current_date), 1, '010-4444-5555', 'CONFIRMED', DATEADD('DAY', -16, current_date), 'R00007', DATEADD('DAY', -16, current_timestamp)),
  (8, '유지민', DATEADD('DAY', -12, current_date), DATEADD('DAY', -11, current_date), 2, '010-6666-7777', 'CONFIRMED', DATEADD('DAY', -13, current_date), 'R00008', DATEADD('DAY', -13, current_timestamp)),
  (9, '선우진', DATEADD('DAY', -9, current_date), DATEADD('DAY', -8, current_date), 1, '010-1212-3434', 'CONFIRMED', DATEADD('DAY', -10, current_date), 'R00009', DATEADD('DAY', -10, current_timestamp)),
  (10, '배수아', DATEADD('DAY', -6, current_date), DATEADD('DAY', -5, current_date), 2, '010-5656-7878', 'CONFIRMED', DATEADD('DAY', -7, current_date), 'R00010', DATEADD('DAY', -7, current_timestamp)),
  (11, '고다빈', DATEADD('DAY', -3, current_date), DATEADD('DAY', -2, current_date), 1, '010-9090-1010', 'CONFIRMED', DATEADD('DAY', -4, current_date), 'R00011', DATEADD('DAY', -4, current_timestamp)),
  (12, '한도윤', DATEADD('DAY', -1, current_date), current_date, 2, '010-2323-4545', 'CONFIRMED', DATEADD('DAY', -2, current_date), 'R00012', DATEADD('DAY', -2, current_timestamp));

-- Sales Records
insert into sales_records (id, product_id, quantity, total_price, created_at) values
  (1, 2, 3, 30000.00, DATEADD('DAY', -1, current_timestamp)),
  (2, 2, 1, 10000.00, current_timestamp),
  (3, 8, 5, 10000.00, DATEADD('DAY', -7, current_timestamp)),
  (4, 9, 2, 8000.00, DATEADD('DAY', -15, current_timestamp)),
  (5, 10, 10, 30000.00, DATEADD('DAY', -25, current_timestamp));

-- Rental Records (연결된 예약과 무관한 워크인 포함)
insert into rental_records (id, reservation_id, product_id, quantity, total_price, is_returned, created_at) values
  (1, 3, 3, 2, 40000.00, false, DATEADD('DAY', -28, current_timestamp)),
  (2, 4, 4, 1, 15000.00, true, DATEADD('DAY', -25, current_timestamp)),
  (3, 5, 5, 3, 75000.00, false, DATEADD('DAY', -21, current_timestamp)),
  (4, 6, 6, 1, 18000.00, true, DATEADD('DAY', -18, current_timestamp)),
  (5, 7, 7, 4, 48000.00, false, DATEADD('DAY', -15, current_timestamp)),
  (6, null, 3, 1, 20000.00, false, DATEADD('DAY', -3, current_timestamp));


