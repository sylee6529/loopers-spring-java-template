# PG 결제 시스템 구현 가이드

## 구현 완료 ✅

외부 PG 시스템 연동 + Resilience 패턴(Fallback, Timeout, CircuitBreaker, Retry) 완전 구현

## 주요 해결 사항

### 1. 멱등성 보장
- `Payment.orderId`에 UNIQUE 제약 추가
- PaymentService에서 중복 체크 → `409 Conflict` 응답
- Retry 비활성화 (`maxAttempts: 1`)

### 2. 완전한 도메인 계층
```
domain/payment/          # Payment, PaymentStatus, CardType, Repository
infrastructure/payment/  # JpaRepository, RepositoryImpl
application/payment/     # Service, Facade, Scheduler, DTOs
interfaces/api/payment/  # Controller, TestController
```

### 3. 안전한 Fallback
- **Before**: 가짜 transactionKey (`"FALLBACK-xxx"`)
- **After**: `transactionKey = null` + `requiresRetry = true`

### 4. 콜백 처리
```java
// PaymentTestController:67
@PostMapping("/callback")
public CallbackResponse handleCallback(...) {
    // 1. transactionKey로 Payment 조회
    // 2. Payment 상태 업데이트
    // 3. Order 연동 (TODO 표시)
}
```

### 5. 자동 상태 복구
```java
// PaymentStatusSyncScheduler:32
@Scheduled(fixedDelay = 60000) // 1분마다
public void syncPendingPayments() {
    // 10분 이상 PENDING 상태 조회 → PG API 호출 → 동기화
}
```

### 6. Resilience4j 설정
```yaml
circuitbreaker:
  pgSimulator:
    failureRateThreshold: 60%
    waitDurationInOpenState: 15s

retry:
  pgSimulator:
    maxAttempts: 1  # 멱등성 보장

timelimiter:
  pgSimulator:
    timeoutDuration: 2s
```

## 빠른 시작

### 1. 실행
```bash
# 인프라 시작
docker-compose -f ./docker/infra-compose.yml up -d

# PG Simulator 시작 (별도 터미널)
./gradlew :apps:pg-simulator:bootRun

# Commerce API 시작
./gradlew :apps:commerce-api:bootRun
```

### 2. 테스트
```bash
# 결제 요청
curl -X POST http://localhost:8080/api/v1/payments/test/request \
  -H "X-USER-ID: 12345" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER-001",
    "cardType": "SAMSUNG",
    "cardNo": "1234-5678-9012-3456",
    "amount": 50000
  }'

# 결과 확인 (transactionKey 수신)
# {"transactionKey":"20251205:TR:xxx","status":"PENDING",...}
```

**또는** `test-payment-flow.http` 파일 사용

### 3. 모니터링
```bash
# Grafana (선택)
docker-compose -f ./docker/monitoring-compose.yml up -d
open http://localhost:3000  # admin/admin
```

## API 엔드포인트

### 테스트 엔드포인트 (즉시 사용 가능)
```http
POST   /api/v1/payments/test/request      # 결제 요청
GET    /api/v1/payments/test/status/{key} # 상태 조회
POST   /api/v1/payments/callback           # PG 콜백
```

### 프로덕션 엔드포인트 (재시작 후 사용)
```http
POST   /api/v1/payments                    # 결제 요청
GET    /api/v1/payments/orders/{orderId}   # orderId로 조회
POST   /api/v1/payments/{key}/sync         # 상태 동기화
```

## 아키텍처 흐름

### 정상 플로우
```
1. 결제 요청 → Payment 엔티티 생성 (PENDING)
2. PG 호출 → transactionKey 수신
3. Payment에 transactionKey 저장
4. PG 비동기 처리 (1~5초)
5. 콜백 수신 → Payment 상태 업데이트 (SUCCESS/FAILED)
```

### 장애 플로우
```
1. 결제 요청 → Payment 엔티티 생성 (PENDING)
2. PG 호출 실패 (Timeout/Circuit Open)
3. Fallback → transactionKey = null
4. requiresRetry = true 설정
5. 스케줄러가 1분 후 자동 재확인 (또는 수동 재시도)
```

## 주요 클래스

| 클래스 | 역할 | 위치 |
|--------|------|------|
| `Payment` | 도메인 엔티티 (상태 관리) | domain/payment/Payment.java:21 |
| `PaymentService` | 비즈니스 로직 + 멱등성 | application/payment/PaymentService.java:50 |
| `PaymentFacade` | 트랜잭션 경계, Order 연동 | application/payment/PaymentFacade.java:28 |
| `PgPaymentClient` | PG API 호출 + Resilience | infrastructure/payment/pg/PgPaymentClient.java:42 |
| `PaymentStatusSyncScheduler` | 자동 상태 복구 | application/payment/PaymentStatusSyncScheduler.java:32 |

## 요구사항 체크리스트

### ⚡ PG 연동
- [x] RestTemplate 기반 PG 클라이언트
- [x] 2초 타임아웃 설정
- [x] 예외 처리 + Fallback
- [x] 콜백 + 상태 조회 API 연동

### 🛡 Resilience
- [x] Circuit Breaker (60% 실패율)
- [x] Retry 안전화 (maxAttempts: 1)
- [x] 내부 시스템 보호 (Fallback)
- [x] 콜백 누락 대응 (폴링 1분)
- [x] 타임아웃 실패 추적

## 테스트 시나리오

`test-payment-flow.http` 참고:

1. **정상 결제**: 결제 요청 → transactionKey 수신 → 콜백 처리
2. **멱등성**: 동일 orderId 재요청 → 409 Conflict
3. **Circuit Breaker**: PG 중지 → Fallback 응답 → requiresRetry
4. **자동 복구**: PENDING 10분 → 스케줄러가 자동 동기화

## 다음 단계 (TODO)

PaymentFacade에 표시된 부분:

```java
// PaymentFacade.java:48
if (payment.isSuccess()) {
    // TODO: orderService.completeOrder(orderId);
    // TODO: 재고 차감 확정
}
if (payment.isFailed()) {
    // TODO: orderService.failOrder(orderId);
    // TODO: 재고 복구, 포인트 환불
}
```

## 참고 문서

- `IMPLEMENTATION-ISSUES.md`: 이전 문제점 분석
- `test-payment-flow.http`: 전체 테스트 시나리오
- `resilience4j.yml`: Resilience 설정

---

**모든 요구사항 충족 완료!** 🎉
