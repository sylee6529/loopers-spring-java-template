# PG 연동 가이드 (Resilience4j 적용)

## 개요

이 가이드는 commerce-api와 pg-simulator 간의 통신 및 Resilience 패턴 적용을 설명합니다.

### 적용된 Resilience 패턴

- ✅ **Circuit Breaker**: PG 장애 시 빠른 실패 및 시스템 보호
- ✅ **Timeout (TimeLimiter)**: 응답 지연 제한 (2초)
- ✅ **Retry**: 일시적 오류 재시도 (최대 2회)
- ✅ **Fallback**: 실패 시 대체 응답 제공

## 아키텍처

```
Commerce API (8080)
    ↓ RestTemplate + Resilience4j
PG Simulator (8082)
    ↓ Callback
Commerce API (/api/v1/payments/callback)
```

### 비동기 결제 흐름

1. **요청**: Commerce API → PG Simulator
   - Circuit Breaker로 보호
   - 2초 타임아웃
   - 최대 2회 재시도

2. **응답**: PENDING 상태 반환
   - transactionKey 발급

3. **처리**: PG가 1~5초 후 비동기 처리
   - 성공 70%, 한도초과 20%, 카드오류 10%

4. **콜백**: PG → Commerce API
   - `/api/v1/payments/callback` 호출
   - 최종 상태 전달

5. **상태 확인**: Commerce API → PG Simulator
   - 콜백 누락 시 폴링으로 상태 확인

## 실행 방법

### 1단계: 인프라 시작

```bash
# MySQL, Redis
docker-compose -f ./docker/infra-compose.yml up -d

# 모니터링 (선택)
docker-compose -f ./docker/monitoring-compose.yml up -d
```

### 2단계: 애플리케이션 실행

#### 방법 A: IntelliJ Compound Configuration (권장)

`RUN-MULTIPLE-APPS.md` 참고하여 설정 후:

1. Run Configuration에서 `All Services` 선택
2. 실행 버튼 클릭 (⌘R)

#### 방법 B: 터미널 별도 실행

```bash
# 터미널 1 - PG Simulator
./gradlew :apps:pg-simulator:bootRun

# 터미널 2 - Commerce API
./gradlew :apps:commerce-api:bootRun
```

### 3단계: 연결 확인

```bash
# PG Simulator
curl http://localhost:8082/actuator/health

# Commerce API
curl http://localhost:8080/actuator/health
```

## 테스트 시나리오

### 시나리오 1: 정상 결제 요청

```bash
curl -X POST http://localhost:8080/api/v1/payments/test/request \
  -H "X-USER-ID: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "TEST-ORDER-001",
    "cardType": "SAMSUNG",
    "cardNo": "1234-5678-9012-3456",
    "amount": 5000
  }'
```

**예상 결과:**
- 60% 확률로 성공 (transactionKey 반환)
- 40% 확률로 실패 → Retry 동작 → 최대 2회 재시도

**성공 응답 예시:**
```json
{
  "transactionKey": "20250816:TR:abc123",
  "status": "PENDING",
  "reason": null
}
```

### 시나리오 2: 결제 상태 조회

```bash
# transactionKey는 위에서 받은 값 사용
curl -X GET http://localhost:8080/api/v1/payments/test/status/20250816:TR:abc123 \
  -H "X-USER-ID: 1"
```

**예상 결과:**
- PENDING: 아직 처리 중 (1~5초 소요)
- SUCCESS: 결제 성공 (70%)
- FAILED: 결제 실패 (30% - 한도초과 또는 카드오류)

### 시나리오 3: Circuit Breaker 동작 확인

Circuit Breaker는 다음 상황에서 OPEN됩니다:
- 최근 10개 호출 중 60% 이상 실패
- OPEN 상태에서는 **즉시 Fallback 응답 반환** (PG 호출 안 함)

#### 3-1. Circuit Breaker 트리거

```bash
# 10번 연속 호출하여 실패율 높이기
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{
      \"orderId\": \"TEST-$i\",
      \"cardType\": \"SAMSUNG\",
      \"cardNo\": \"1234-5678-9012-3456\",
      \"amount\": 5000
    }"
  echo ""
  sleep 0.5
done
```

#### 3-2. Circuit Breaker 상태 확인

```bash
curl http://localhost:8081/actuator/circuitbreakers | jq
```

**응답 예시:**
```json
{
  "circuitBreakers": {
    "pgSimulator": {
      "state": "OPEN",
      "failureRate": "75.0%",
      "slowCallRate": "0.0%"
    }
  }
}
```

#### 3-3. OPEN 상태에서 요청 시 Fallback 동작

Circuit이 OPEN일 때:
```json
{
  "transactionKey": "FALLBACK-TEST-ORDER-001",
  "status": "PENDING",
  "reason": "PG 시스템 일시 장애 - 결제 진행 중입니다. 잠시 후 상태를 확인해주세요."
}
```

- PG를 실제로 호출하지 않음 (빠른 실패)
- PENDING 상태로 반환
- 사용자에게 "나중에 확인" 안내

### 시나리오 4: Timeout 동작 확인

PG Simulator는 100~500ms 응답 지연을 갖지만, TimeLimiter는 2초로 설정되어 있어 정상 동작합니다.

만약 PG가 2초 이상 응답하지 않으면:
- TimeLimiter가 TimeoutException 발생
- Retry 동작 (최대 2회)
- 모두 실패 시 Fallback 호출

### 시나리오 5: 콜백 테스트

PG Simulator가 결제 완료 시 `/api/v1/payments/callback`을 호출합니다.

Commerce API 로그에서 확인:
```
[Callback] PG 콜백 수신 - data: {transactionKey=..., status=SUCCESS}
[Callback] 결제 처리 - transactionKey: ..., status: SUCCESS
```

## Resilience4j 설정 상세

### Circuit Breaker 설정 (`resilience4j.yml`)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgSimulator:
        slidingWindowSize: 10  # 최근 10개 호출 기준
        minimumNumberOfCalls: 5  # 최소 5번 호출 후 판단
        failureRateThreshold: 60  # 실패율 60% 이상 → OPEN
        slowCallDurationThreshold: 1s  # 1초 이상 → 느린 호출
        waitDurationInOpenState: 15s  # OPEN 후 15초 → HALF_OPEN
```

**상태 전환:**
- CLOSED → OPEN: 실패율 60% 이상
- OPEN → HALF_OPEN: 15초 후 자동 전환
- HALF_OPEN → CLOSED: 3번 테스트 호출 성공
- HALF_OPEN → OPEN: 테스트 호출 실패

### Retry 설정

```yaml
resilience4j:
  retry:
    instances:
      pgSimulator:
        maxAttempts: 2  # 최대 2회 재시도 (원본 1회 + 재시도 1회)
        waitDuration: 300ms  # 재시도 간격
```

### Timeout 설정

```yaml
resilience4j:
  timelimiter:
    instances:
      pgSimulator:
        timeoutDuration: 2s  # 2초 타임아웃
```

## 모니터링

### Grafana 대시보드

Circuit Breaker 상태를 실시간 모니터링:

http://localhost:3000/d/circuit-breaker-monitoring

**주요 메트릭:**
- Circuit Breaker 상태 (CLOSED/OPEN/HALF_OPEN)
- 성공/실패 호출 비율
- 실패율 추이
- 거부된 호출 수

### Actuator 엔드포인트

```bash
# Circuit Breaker 상태
curl http://localhost:8081/actuator/circuitbreakers

# Circuit Breaker 이벤트 (상태 전환 이력)
curl http://localhost:8081/actuator/circuitbreakerevents

# Retry 이벤트
curl http://localhost:8081/actuator/retryevents

# Prometheus 메트릭
curl http://localhost:8081/actuator/prometheus | grep resilience4j
```

### 주요 메트릭

```promql
# Circuit Breaker 상태 (0:CLOSED, 1:OPEN, 2:HALF_OPEN)
resilience4j_circuitbreaker_state{name="pgSimulator"}

# 실패율
resilience4j_circuitbreaker_failure_rate{name="pgSimulator"}

# 호출 수
resilience4j_circuitbreaker_calls_seconds_count{name="pgSimulator"}
```

## PG 장애 시나리오별 대응

### 1. PG 응답 지연 (100~500ms)
- **대응**: RestTemplate timeout (3초) 내 처리
- **결과**: 정상 처리

### 2. PG 서버 다운
- **대응**:
  1. Connection refused 예외 발생
  2. Retry (1회)
  3. Fallback → PENDING 반환
- **결과**: 빠른 실패, 사용자에게 "나중에 확인" 안내

### 3. PG 간헐적 장애 (40% 실패율)
- **대응**:
  1. 실패 시 Retry (1회)
  2. 실패율 60% 도달 시 Circuit OPEN
  3. 이후 요청은 Fallback으로 즉시 처리
- **결과**: 시스템 보호, 빠른 응답

### 4. PG 장기 장애
- **대응**:
  1. Circuit OPEN 유지 (15초)
  2. 15초 후 HALF_OPEN (테스트 호출 3회)
  3. 여전히 실패 시 다시 OPEN
- **결과**: 주기적 자동 복구 시도

### 5. 콜백 누락
- **대응**:
  - 상태 조회 API (`getPaymentStatus`)로 폴링
  - 또는 스케줄러로 PENDING 건 주기적 확인
- **결과**: 최종 상태 동기화

## 체크리스트 검증

### ⚡ PG 연동 대응

- ✅ PG 연동 API는 RestTemplate로 외부 시스템 호출
  - `PgPaymentClient` 구현

- ✅ 응답 지연에 대해 타임아웃 설정
  - `@TimeLimiter(name = "pgSimulator")` - 2초 타임아웃
  - RestTemplate readTimeout - 3초

- ✅ 실패 시 적절한 예외 처리
  - `PgCommunicationException` 정의
  - Fallback 메서드 구현

- ✅ 결제 요청 실패 응답 처리
  - Fallback에서 PENDING 상태 반환

- ✅ 콜백 + 결제 상태 확인 API 활용
  - `/api/v1/payments/callback` 엔드포인트
  - `getPaymentStatus()` 메서드

### 🛡 Resilience 설계

- ✅ 서킷 브레이커 적용
  - `@CircuitBreaker(name = "pgSimulator")`
  - 실패율 60% 이상 시 OPEN

- ✅ 재시도 정책 적용
  - `@Retry(name = "pgSimulator")`
  - 최대 2회 재시도

- ✅ 외부 시스템 장애 시 내부 시스템 정상 응답
  - Fallback으로 PENDING 상태 반환
  - Circuit OPEN 시 빠른 실패

- ✅ 콜백 누락 시 상태 복구 가능
  - `getPaymentStatus()` API로 조회

- ✅ 타임아웃 실패 시에도 결제 정보 확인 가능
  - Fallback에서 transactionKey 생성
  - 이후 상태 조회로 실제 결제 결과 확인

## 트러블슈팅

### Circuit Breaker가 OPEN되지 않음

**원인**: 최소 호출 수 미달

**해결**: `minimumNumberOfCalls: 5` 설정을 확인하고, 최소 5번 이상 호출

### Retry가 동작하지 않음

**원인**: CoreException 등 ignoreExceptions에 포함된 예외

**해결**: `resilience4j.yml`에서 retryExceptions 확인

### Fallback이 호출되지 않음

**원인**: Fallback 메서드 시그니처 불일치

**해결**: 원본 메서드와 동일한 파라미터 + Exception 파라미터 추가

### PG Simulator 연결 실패

```bash
# PG가 실행 중인지 확인
curl http://localhost:8082/actuator/health

# 포트 충돌 확인
lsof -i :8082
```

## 추가 개선 사항 (Nice-To-Have)

### 1. Rate Limiter 추가

PG 호출 빈도 제한:

```yaml
resilience4j:
  ratelimiter:
    instances:
      pgSimulator:
        limitForPeriod: 10  # 1초당 10개 요청
        limitRefreshPeriod: 1s
        timeoutDuration: 100ms
```

### 2. Bulkhead 추가

PG 호출 스레드 격리:

```yaml
resilience4j:
  bulkhead:
    instances:
      pgSimulator:
        maxConcurrentCalls: 5  # 동시 호출 5개 제한
```

### 3. 결제 상태 폴링 스케줄러

PENDING 상태 건을 주기적으로 조회하여 최종 상태 동기화

### 4. 데드레터큐 (DLQ)

콜백 실패 건을 별도 큐에 저장하여 재처리

## 참고 자료

- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Spring Boot with Resilience4j](https://resilience4j.readme.io/docs/getting-started-3)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
