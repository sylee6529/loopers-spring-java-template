# 🚨 현재 구현의 심각한 문제점

## ❌ 발견된 문제

### 1. **멱등성 미구현** (가장 심각!)

**문제:**
```java
@Retry(name = "pgSimulator")  // maxAttempts = 2
public CompletableFuture<PgPaymentResponse> requestPayment(String userId, PgPaymentRequest request) {
    // orderId 중복 체크 없음!
    // 같은 orderId로 2번 PG 호출 가능 → 중복 결제!
}
```

**시나리오:**
```
1차 시도: orderId="ORDER-001", amount=5000 → PG 호출 실패 (네트워크 오류)
2차 시도: orderId="ORDER-001", amount=5000 → PG 호출 성공

결과: 같은 주문에 대해 PG가 2번 처리할 수 있음!
```

**영향:**
- 💰 **중복 결제 발생 가능**
- 🏦 금융 거래의 기본 원칙 위반
- ⚠️ 프로덕션 절대 불가

---

### 2. **도메인 계층 완전 누락**

**문제:**
```
현재: PgPaymentClient (Infrastructure) → PG 직접 호출
누락: Payment Entity, Service, Repository
```

**없는 것들:**
- ❌ Payment 엔티티 (결제 이력 저장)
- ❌ Payment 리포지토리
- ❌ Payment 서비스 (비즈니스 로직)
- ❌ Payment 상태 관리

**영향:**
- 결제 이력 조회 불가
- 중복 결제 방지 불가
- 주문과 결제 연동 불가
- 콜백 처리 시 저장할 곳 없음

---

### 3. **Fallback 로직 문제**

**현재 구현:**
```java
private CompletableFuture<PgPaymentResponse> requestPaymentFallback(...) {
    // 가짜 transactionKey 생성!
    PgPaymentResponse fallbackResponse = new PgPaymentResponse(
        "FALLBACK-" + request.orderId(),  // ❌ 가짜!
        PgTransactionStatus.PENDING,
        "PG 시스템 일시 장애..."
    );
    return CompletableFuture.completedFuture(fallbackResponse);
}
```

**문제:**
1. **가짜 transactionKey**: "FALLBACK-ORDER-001"
   - 실제로는 PG에 없는 거래
   - 나중에 조회 불가

2. **PENDING 상태 거짓말**:
   - 실제로는 PG 호출 안 됨
   - 사용자는 "진행 중"으로 오해

3. **복구 불가**:
   - 가짜 transactionKey로는 상태 조회 불가
   - 콜백도 올 리 없음

**올바른 Fallback:**
```java
// 1. Payment 엔티티에 실패 상태 저장
// 2. 나중에 수동/자동 재시도 가능하도록
// 3. 사용자에게 명확히 실패 알림
```

---

### 4. **비동기 처리 미구현**

**현재 콜백 엔드포인트:**
```java
@PostMapping("/callback")
public CallbackResponse handleCallback(@RequestBody Map<String, Object> callbackData) {
    log.info("[Callback] PG 콜백 수신 - data: {}", callbackData);
    // TODO: 실제 구현에서는 transactionKey로 결제 정보 조회 후 주문 상태 업데이트
    return new CallbackResponse("SUCCESS", "콜백 처리 완료");
}
```

**문제:**
- ❌ 로그만 찍고 끝
- ❌ Payment 상태 업데이트 없음
- ❌ Order 상태 업데이트 없음
- ❌ 실제로 아무것도 안 함

**필요한 로직:**
```
1. transactionKey로 Payment 조회
2. PG 결과에 따라 Payment 상태 업데이트
3. Payment 상태에 따라 Order 상태 업데이트
4. 재고 복구 (실패 시)
5. 포인트 환불 (실패 시)
```

---

### 5. **상태 복구 메커니즘 없음**

**문제 시나리오:**
```
1. PG 결제 요청 성공 → PENDING
2. PG가 처리 완료 (SUCCESS/FAILED)
3. 콜백 전송 시도 → 네트워크 오류로 실패
4. 결과: Payment는 영원히 PENDING 상태
```

**필요한 것:**
- ⏰ 스케줄러로 PENDING 상태 주기적 확인
- 🔄 일정 시간 후 PG 상태 조회 API 호출
- 📊 최종 상태 동기화

**현재:**
- ❌ 스케줄러 없음
- ❌ 폴링 로직 없음
- ❌ PENDING이 영원히 유지될 수 있음

---

### 6. **Retry 설정 위험**

**현재 설정:**
```yaml
resilience4j:
  retry:
    instances:
      pgSimulator:
        maxAttempts: 2
        retryExceptions:
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
```

**문제:**
- 멱등성 없이 Retry → 중복 결제
- TimeoutException도 재시도 → 실제로는 PG에서 처리 중일 수 있음

**예시:**
```
1차 시도:
- Commerce API → PG 요청 전송
- PG 수신 → 처리 시작 (트랜잭션 생성)
- PG → Commerce API 응답 중 네트워크 타임아웃

2차 시도 (Retry):
- Commerce API → 동일 orderId로 재요청
- PG 수신 → 중복 결제 발생!
```

---

## ✅ 요구사항 체크리스트 재확인

### ⚡ PG 연동 대응

- [x] **PG 연동 API는 RestTemplate으로 구현** ✅
- [x] **타임아웃 설정** ✅
- [x] **실패 시 예외 처리** ✅
- [❌] **결제 요청 실패 응답에 대한 적절한 시스템 연동** ❌
  - 문제: Payment 엔티티 없음
  - 문제: 실패 시 저장/추적 불가

- [❌] **콜백 + 상태 확인 API 활용** ❌
  - 문제: 콜백 처리 로직 미구현 (로그만)
  - 문제: 상태 확인 후 시스템 업데이트 없음

### 🛡 Resilience 설계

- [x] **서킷 브레이커 적용** ✅
- [⚠️] **재시도 정책 적용** ⚠️ (멱등성 없어서 위험!)
  - 문제: 중복 결제 가능
  - 문제: orderId 중복 체크 없음

- [❌] **외부 시스템 장애 시 내부 시스템 정상 응답** ❌
  - 문제: Fallback이 가짜 데이터 반환
  - 문제: 실패를 성공처럼 보이게 함

- [❌] **콜백 누락 시 상태 복구** ❌
  - 문제: 폴링 스케줄러 없음
  - 문제: PENDING 영구 유지 가능

- [❌] **타임아웃 실패 시 결제 정보 확인** ❌
  - 문제: Payment 저장 안 함
  - 문제: transactionKey 추적 불가

---

## 📋 필수 구현 사항

### 1. Payment 도메인 계층

```java
// 1. Payment 엔티티
@Entity
public class Payment extends BaseEntity {
    private String orderId;           // 주문 ID (중복 체크 키)
    private String transactionKey;    // PG 트랜잭션 키
    private PaymentStatus status;     // PENDING, SUCCESS, FAILED
    private Long amount;
    private String reason;            // 실패 사유

    // 멱등성 보장을 위한 유니크 제약
    @Column(unique = true)
    private String orderId;
}

// 2. Payment 리포지토리
public interface PaymentRepository {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByTransactionKey(String transactionKey);
}

// 3. Payment 서비스 (멱등성 보장)
@Service
public class PaymentService {

    @Transactional
    public Payment requestPayment(String orderId, ...) {
        // 1. 중복 체크
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new DuplicatePaymentException();
        }

        // 2. Payment 엔티티 생성 (PENDING 상태)
        Payment payment = Payment.create(orderId, amount);
        paymentRepository.save(payment);

        // 3. PG 호출 (Retry해도 안전 - orderId 중복 체크됨)
        PgPaymentResponse pgResponse = pgPaymentClient.requestPayment(...);

        // 4. transactionKey 저장
        payment.updateTransactionKey(pgResponse.transactionKey());

        return payment;
    }
}
```

### 2. Fallback 개선

```java
private CompletableFuture<PgPaymentResponse> requestPaymentFallback(...) {
    log.error("[PG Fallback] 결제 요청 실패 - orderId: {}", request.orderId());

    // ❌ 가짜 데이터 반환하지 말 것!
    // ✅ 예외를 던져서 상위 레이어에서 처리
    throw new PgUnavailableException("PG 시스템 일시 장애");
}

// Service 레이어에서 처리
@Transactional
public Payment requestPayment(...) {
    Payment payment = Payment.create(orderId, amount);
    paymentRepository.save(payment);

    try {
        PgPaymentResponse response = pgPaymentClient.requestPayment(...);
        payment.updateTransactionKey(response.transactionKey());
    } catch (PgUnavailableException e) {
        // Payment는 PENDING 상태로 저장됨
        // 나중에 수동/자동 재시도 가능
        payment.markAsPgUnavailable();
    }

    return payment;
}
```

### 3. 콜백 처리

```java
@PostMapping("/callback")
@Transactional
public CallbackResponse handleCallback(@RequestBody PgCallbackRequest callback) {
    // 1. Payment 조회
    Payment payment = paymentRepository.findByTransactionKey(callback.transactionKey())
        .orElseThrow(() -> new PaymentNotFoundException());

    // 2. 상태 업데이트
    payment.updateStatus(callback.status(), callback.reason());

    // 3. Order 상태 업데이트
    if (payment.isSuccess()) {
        orderService.completePayment(payment.getOrderId());
    } else {
        orderService.failPayment(payment.getOrderId(), payment.getReason());
    }

    return new CallbackResponse("SUCCESS", "처리 완료");
}
```

### 4. 상태 폴링 스케줄러

```java
@Scheduled(fixedDelay = 60000) // 1분마다
@Transactional
public void syncPendingPayments() {
    // 10분 이상 PENDING 상태인 결제들
    List<Payment> pendingPayments = paymentRepository.findPendingPaymentsOlderThan(
        LocalDateTime.now().minusMinutes(10)
    );

    for (Payment payment : pendingPayments) {
        try {
            // PG 상태 조회
            PgPaymentDetailResponse pgStatus =
                pgPaymentClient.getPaymentStatus(payment.getTransactionKey());

            // 상태 동기화
            payment.updateStatus(pgStatus.status(), pgStatus.reason());

            // Order 상태 업데이트
            updateOrderStatus(payment);

        } catch (Exception e) {
            log.error("Payment 상태 동기화 실패: {}", payment.getId(), e);
        }
    }
}
```

### 5. Retry 전략 개선

```yaml
resilience4j:
  retry:
    instances:
      pgSimulator:
        maxAttempts: 1  # ❌ Retry 비활성화
        # 이유: 멱등성 보장 어려움
        # 대안: Service 레이어에서 Payment 저장 후 수동 재시도
```

**또는 멱등성 보장:**
```java
@Transactional
public Payment requestPayment(String orderId, ...) {
    // 1. Payment 먼저 저장 (멱등성 키)
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseGet(() -> {
            Payment newPayment = Payment.create(orderId, amount);
            return paymentRepository.save(newPayment);
        });

    // 2. 이미 처리 중이면 재시도 안 함
    if (payment.hasTransactionKey()) {
        return payment;
    }

    // 3. PG 호출 (이제 Retry해도 안전)
    PgPaymentResponse response = pgPaymentClient.requestPayment(...);
    payment.updateTransactionKey(response.transactionKey());

    return payment;
}
```

---

## 🎯 수정 우선순위

### P0 (즉시 수정 필요)
1. ✅ Payment 도메인 계층 구현
2. ✅ 멱등성 보장 (orderId 중복 체크)
3. ✅ Fallback 로직 개선
4. ✅ 콜백 처리 구현

### P1 (다음 단계)
5. ✅ 상태 폴링 스케줄러
6. ✅ Order와 Payment 연동
7. ✅ 재고/포인트 복구 로직

### P2 (추가 개선)
8. ⚠️ Retry 전략 재검토
9. ⚠️ 트랜잭션 경계 최적화
10. ⚠️ 에러 핸들링 개선

---

## 🚨 경고

**현재 상태로는 프로덕션 사용 절대 불가!**

- 💰 중복 결제 발생 가능
- 📊 결제 이력 추적 불가
- 🔄 상태 복구 불가
- ⚠️ 데이터 정합성 보장 안 됨

**반드시 도메인 계층 구현 후 사용할 것!**
