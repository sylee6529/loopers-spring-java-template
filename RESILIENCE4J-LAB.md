# Resilience4j 실습 가이드 (Hands-on Lab)

## 🎯 실습 목표

PG Simulator의 특성을 분석하고, Grafana 지표를 보면서 Resilience4j 설정을 최적화합니다.

---

## 📋 사전 준비

### 1. 모든 서비스 시작

```bash
# 1. 인프라 (MySQL, Redis)
docker-compose -f ./docker/infra-compose.yml up -d

# 2. 모니터링 (Prometheus, Grafana)
docker-compose -f ./docker/monitoring-compose.yml up -d

# 3. PG Simulator
./gradlew :apps:pg-simulator:bootRun

# 4. Commerce API (다른 터미널)
./gradlew :apps:commerce-api:bootRun
```

### 2. Grafana 접속

```
http://localhost:3000
ID: admin
PW: admin
```

### 3. 대시보드 확인

좌측 메뉴 > Dashboards > Browse

다음 대시보드가 있는지 확인:
- ✅ Resilience4j - Detailed Analysis
- ✅ Circuit Breaker Monitoring
- ✅ Spring Boot - Load Test Dashboard

---

## 🧪 Lab 1: 정상 동작 이해하기

### 목표
PG Simulator의 정상 동작(40% 실패율)을 관찰하고 Circuit Breaker가 CLOSED 상태를 유지하는지 확인

### 1-1. 정상 요청 20회

```bash
for i in {1..20}; do
  echo "Request $i..."
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{
      \"orderId\": \"LAB1-$i\",
      \"cardType\": \"SAMSUNG\",
      \"cardNo\": \"1234-5678-9012-3456\",
      \"amount\": 5000
    }" | jq -r '.transactionKey, .status'

  sleep 1
done
```

### 1-2. Grafana에서 확인

**Resilience4j - Detailed Analysis** 대시보드:

#### Row 1: Circuit Breaker 상태
- **Circuit State**: 🟢 CLOSED (0)
- **Failure Rate**: ~40% (노란색 영역)
- **Slow Call Rate**: ~0% (거의 없음)
- **Buffered Calls**: 10 (Window Size)

#### Row 2: 호출 통계
- **Call Rate by Result**:
  - 🟢 Successful: ~0.6 req/sec (60%)
  - 🔴 Failed: ~0.4 req/sec (40%)
  - 🟠 Not Permitted: 0 (Circuit CLOSED)

- **Success Rate %**: ~60%

#### Row 3: 응답 시간
- **Response Time Percentiles**:
  - p50: ~300ms
  - p95: ~500ms
  - p99: ~500ms

#### Row 4: Retry 분석
- **Retry Results**:
  - 🟢 Success Without Retry: ~0.6 req/sec
  - 🟡 Success With Retry: ~0.2 req/sec
  - 🔴 Failed With Retry: ~0.2 req/sec

### 1-3. 분석

```
성공률 = (Success Without Retry + Success With Retry) / Total
       = (60% + 24%) / 100%
       = 84%

이는 다음 계산과 일치:
- 1회 성공: 60%
- 1회 실패 + 2회 성공: 40% × 60% = 24%
- 합계: 84%
```

### ✅ 확인 사항
- [ ] Circuit State가 CLOSED (초록색)
- [ ] Failure Rate가 40% 전후
- [ ] Retry로 성공률이 60% → 84% 향상
- [ ] Circuit이 OPEN되지 않음 (60% 미만 실패율)

---

## 🧪 Lab 2: Circuit Breaker OPEN 트리거

### 목표
의도적으로 실패율을 높여 Circuit을 OPEN 상태로 만들고 동작 관찰

### 2-1. 연속 요청 (실패율 높이기)

```bash
# 자동화된 테스트 스크립트
./test-pg-resilience.sh

# 또는 수동:
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"LAB2-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}" \
    > /dev/null

  sleep 0.2  # 빠른 호출
done
```

### 2-2. Grafana에서 실시간 관찰

**주목할 지표:**

#### Circuit State 변화
```
CLOSED (🟢) → OPEN (🔴)
```

**변화 시점 확인:**
- Failure Rate가 60%를 넘는 순간
- Buffered Calls가 10개 이상 쌓인 후

#### Call Rate by Result 그래프
```
Before OPEN:
- Successful: 60%
- Failed: 40%
- Not Permitted: 0%

After OPEN:
- Successful: 0%
- Failed: 0%
- Not Permitted: 100% ← Circuit이 모든 요청 차단
```

### 2-3. OPEN 상태에서 요청

```bash
# Circuit OPEN 상태에서 요청
for i in {1..5}; do
  RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"OPEN-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}")

  echo "Response: $RESPONSE" | jq
  sleep 0.5
done
```

**예상 응답:**
```json
{
  "transactionKey": "FALLBACK-OPEN-1",
  "status": "PENDING",
  "reason": "PG 시스템 일시 장애 - 결제 진행 중입니다. 잠시 후 상태를 확인해주세요."
}
```

**특징:**
- ⚡ 즉시 응답 (PG 호출 없음)
- 🛡️ Fallback 로직 실행
- 📊 Grafana에서 "Not Permitted" 증가

### 2-4. 로그 확인

```bash
# Commerce API 로그에서 확인
tail -f logs/application.log | grep -E "PG|Fallback"
```

**로그 예시:**
```
[PG Fallback] 결제 요청 실패, PENDING 상태로 반환 - orderId: OPEN-1,
error: CallNotPermittedException: CircuitBreaker 'pgSimulator' is OPEN
```

### ✅ 확인 사항
- [ ] Circuit State가 OPEN (빨간색)
- [ ] Failure Rate > 60%
- [ ] Not Permitted 호출 증가
- [ ] Fallback 응답 확인
- [ ] 응답 시간이 매우 빠름 (< 10ms)

---

## 🧪 Lab 3: 복구 과정 관찰 (HALF_OPEN)

### 목표
Circuit이 자동으로 HALF_OPEN으로 전환되고 복구되는 과정 관찰

### 3-1. 대기 (15초)

```bash
echo "Circuit OPEN 상태에서 15초 대기..."
echo "Grafana에서 Circuit State를 주시하세요"
sleep 15
echo "이제 HALF_OPEN으로 전환되었을 것입니다"
```

### 3-2. Grafana에서 관찰

**Circuit State Timeline:**
```
Time  State
0:00  CLOSED (🟢)
0:05  OPEN (🔴)      ← 실패율 60% 초과
0:20  HALF_OPEN (🟡) ← 15초 후 자동 전환
0:21  CLOSED (🟢)    ← 테스트 호출 3회 성공
```

### 3-3. HALF_OPEN 상태 확인

```bash
# HALF_OPEN 전환 직후 요청
curl -X GET http://localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers.pgSimulator'
```

**응답 예시:**
```json
{
  "state": "HALF_OPEN",
  "failureRate": "62.5%",
  "slowCallRate": "0.0%",
  "bufferedCalls": 10,
  "failedCalls": 5
}
```

### 3-4. 복구 테스트

```bash
# HALF_OPEN에서 테스트 호출 (3회)
for i in {1..3}; do
  echo "Test call $i/3 in HALF_OPEN..."
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"RECOVERY-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}" \
    | jq -r '.status'

  sleep 2
done

# 상태 확인
curl -s http://localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers.pgSimulator.state'
```

### 3-5. 시나리오 분석

**시나리오 A: 3회 모두 성공 → CLOSED**
```
HALF_OPEN → Test 1 ✅ → Test 2 ✅ → Test 3 ✅ → CLOSED
```

**시나리오 B: 1회 실패 → 다시 OPEN**
```
HALF_OPEN → Test 1 ✅ → Test 2 ❌ → OPEN (15초 대기)
```

### ✅ 확인 사항
- [ ] 15초 후 HALF_OPEN 전환
- [ ] 3회 테스트 호출 실행
- [ ] 성공 시 CLOSED 복귀
- [ ] 실패 시 다시 OPEN

---

## 🧪 Lab 4: 설정 최적화 실험

### 목표
설정을 변경하며 영향을 관찰하고 최적값 찾기

### 4-1. Failure Rate Threshold 실험

**가설**: Threshold를 50%로 낮추면 더 민감하게 반응할 것

#### 변경
`apps/commerce-api/src/main/resources/resilience4j.yml`:
```yaml
failureRateThreshold: 50  # 60 → 50
```

#### 재시작
```bash
# Commerce API 재시작
./gradlew :apps:commerce-api:bootRun
```

#### 테스트
```bash
# 동일한 테스트 반복
for i in {1..20}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"EXP1-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}" \
    > /dev/null
  sleep 1
done
```

#### 결과 비교

| Threshold | Circuit OPEN 시점 | 장점 | 단점 |
|-----------|-------------------|------|------|
| **50%** | 빠름 (10-12회) | 빠른 장애 감지 | False positive 증가 |
| **60%** | 보통 (15-18회) | 균형잡힘 | 보통 |
| **70%** | 느림 (20+회) | False positive 적음 | 장애 감지 늦음 |

**권장**: PG의 정상 실패율(40%)보다 충분히 높은 **60%**

---

### 4-2. Retry 횟수 실험

**가설**: maxAttempts를 3으로 늘리면 성공률이 증가할 것

#### 변경
```yaml
maxAttempts: 3  # 2 → 3
```

#### 계산
```
1회 성공률: 60%
2회 중 성공: 84%
3회 중 성공: 93.6%

증가분: 9.6%
비용: PG 호출 50% 증가
```

#### 테스트 및 측정

**Grafana에서 비교:**
- Success Rate %: 84% → 94%
- PG 호출 수: 1.4배 → 2.1배
- 평균 응답 시간: 증가

**트레이드오프 분석:**
```
성공률 향상: 9.6% ✅
PG 부하: +50% ❌
응답 시간: +30% ❌
중복 결제 위험: 증가 ❌
```

**결론**: PG처럼 40% 실패율이 높은 경우, **maxAttempts=2**가 적절
- 더 늘려도 성공률 증가 대비 비용이 큼
- 금융 거래는 중복 방지가 중요

---

### 4-3. Sliding Window Size 실험

**가설**: Window Size를 줄이면 더 빠르게 반응할 것

#### 비교

| Window Size | 샘플 수 | 감지 속도 | 정확도 | 권장 |
|-------------|---------|-----------|--------|------|
| **5** | 적음 | 빠름 | 낮음 | ❌ 민감함 |
| **10** | 보통 | 보통 | 높음 | ✅ 균형 |
| **20** | 많음 | 느림 | 매우 높음 | ⚠️ 둔감함 |

**실험:**
```yaml
# 5로 변경
slidingWindowSize: 5
minimumNumberOfCalls: 3
```

**결과:**
- 5-7회 호출만으로 Circuit OPEN
- 하지만 일시적 오류에도 OPEN (false positive)

**권장**: **slidingWindowSize=10**
- 통계적 유의성 확보
- 빠른 감지 + 정확도 균형

---

## 🧪 Lab 5: Slow Call 감지 실험

### 목표
느린 응답 감지 설정 이해

### 5-1. 현재 설정

```yaml
slowCallDurationThreshold: 1s
slowCallRateThreshold: 50%
```

**의미:**
- 1초 이상 걸리는 호출을 "느림"으로 판단
- 느린 호출이 50% 이상이면 Circuit OPEN

### 5-2. 문제

PG Simulator는 100-500ms 응답이므로:
```
정상 응답 시간: < 500ms
slowCallDurationThreshold: 1s

→ Slow Call이 거의 발생하지 않음
```

### 5-3. 테스트용 설정 변경

```yaml
slowCallDurationThreshold: 200ms  # 테스트용
```

#### 재시작 후 테스트
```bash
for i in {1..20}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"SLOW-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}" \
    > /dev/null
  sleep 1
done
```

**Grafana 확인:**
- **Slow Call Rate**: 증가 (300-500ms 응답들)
- **Circuit State**: Slow Call 50% 초과 시 OPEN

### 5-4. 적절한 설정

```
slowCallDurationThreshold = P95 응답 시간 × 2

PG의 경우:
- P95: 500ms
- Threshold: 1s ✅

이유: 명확히 비정상적으로 느린 경우만 감지
```

### ✅ 실습 후 원복
```yaml
slowCallDurationThreshold: 1s  # 원래대로
```

---

## 📊 Lab 6: 종합 대시보드 분석

### 목표
Grafana에서 전체 지표를 종합적으로 분석

### 6-1. Dashboard: Resilience4j - Detailed Analysis

#### Row 1: 상태 Overview
```
Circuit State: 현재 상태 (CLOSED/OPEN/HALF_OPEN)
Failure Rate: 실패율 추이
Slow Call Rate: 느린 호출 비율
Buffered Calls: 윈도우 내 호출 수
```

**해석:**
- Circuit State = CLOSED + Failure Rate < 60% → ✅ 정상
- Circuit State = OPEN → ⚠️ 장애 중
- Buffered Calls < 5 → ⚠️ 샘플 부족 (판단 보류)

#### Row 2: 호출 통계
```
Call Rate by Result:
- Successful: 초록색
- Failed: 빨간색
- Not Permitted: 주황색

Success Rate %:
- 목표: > 80%
- 경고: < 60%
```

**패턴 분석:**
```
정상 패턴:
- Successful: 높음
- Failed: 일정 (40%)
- Not Permitted: 0

장애 패턴:
- Successful: 0
- Failed: 0
- Not Permitted: 높음 (Circuit OPEN)
```

#### Row 3: 응답 시간
```
Response Time Percentiles:
- p50: 중간값
- p95: 95% 요청
- p99: 99% 요청 (최악)

목표:
- p50 < 500ms
- p95 < 1s
- p99 < 2s
```

#### Row 4: Retry 분석
```
Retry Results:
- Success Without Retry: 1회 성공 (60%)
- Success With Retry: 재시도 후 성공 (24%)
- Failed With Retry: 재시도해도 실패 (16%)

Retry Success Rate:
- 재시도의 효과 측정
- 목표: > 50%
```

---

## 🎓 Lab 7: 실전 시나리오

### 시나리오 1: 프로덕션 배포 전 검증

**상황**: PG 연동을 프로덕션에 배포하기 전 설정 검증

**체크리스트:**
```bash
# 1. 정상 트래픽 시뮬레이션 (1분간)
for i in {1..60}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"PROD-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}" \
    > /dev/null
  sleep 1
done

# 2. Grafana 확인
- Circuit State: CLOSED 유지?
- Success Rate: > 80%?
- p95 Response Time: < 1s?
- Retry로 성공률 향상?

# 3. Circuit OPEN 복구 테스트
./test-pg-resilience.sh
# - 15초 내 HALF_OPEN 전환?
# - 3회 테스트 후 CLOSED 복귀?

# 4. Fallback 동작 확인
# - Circuit OPEN 시 즉시 Fallback 응답?
# - PENDING 상태로 반환?
```

---

### 시나리오 2: 장애 대응 훈련

**상황**: PG 장애 발생 시 대응 절차 연습

**절차:**
```bash
# 1. PG Simulator 강제 종료
# (pg-simulator 프로세스 종료)

# 2. Commerce API 요청 시도
curl -X POST http://localhost:8080/api/v1/payments/test/request \
  -H "X-USER-ID: 1" \
  -H "Content-Type: application/json" \
  -d '{"orderId": "FAIL-1", "cardType": "SAMSUNG", "cardNo": "1234-5678-9012-3456", "amount": 5000}'

# 3. Grafana 확인
# - Failure Rate 급증
# - Circuit 곧 OPEN
# - Not Permitted 호출 증가

# 4. Alert 확인 (설정된 경우)

# 5. PG Simulator 재시작

# 6. 복구 관찰
# - 15초 후 HALF_OPEN
# - 테스트 성공 → CLOSED
```

---

## 📝 최종 권장 설정 (종합)

### PG Simulator에 최적화된 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgSimulator:
        # Window 설정
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5

        # Threshold
        failureRateThreshold: 60
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 1s

        # State 전환
        waitDurationInOpenState: 15s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true

  retry:
    instances:
      pgSimulator:
        maxAttempts: 2
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2

  timelimiter:
    instances:
      pgSimulator:
        timeoutDuration: 2s
        cancelRunningFuture: true
```

### 결정 근거

| 설정 | 값 | 근거 |
|------|-----|------|
| failureRateThreshold | 60% | 정상(40%) + 안전마진(20%) |
| slidingWindowSize | 10 | 통계적 유의성 + 빠른 감지 |
| maxAttempts | 2 | 성공률 84%, PG 부하 적절 |
| waitDurationInOpenState | 15s | PG 복구 시간 고려 |
| timeoutDuration | 2s | 정상(300ms) + 재시도(600ms) + 여유 |

---

## 🎯 학습 정리

### 핵심 개념

1. **Circuit Breaker는 방어적 장치**
   - 실패율이 임계값 초과 → OPEN
   - PG를 호출하지 않고 Fallback
   - 시스템 보호 + 빠른 응답

2. **설정은 서비스 특성 기반**
   - PG의 정상 실패율: 40%
   - Threshold: 60% (정상 + 마진)
   - Window Size: 통계적 유의성 고려

3. **지표 기반 의사결정**
   - Grafana로 실시간 모니터링
   - 실험 → 측정 → 최적화
   - 트레이드오프 분석

### Next Steps

1. ✅ 현재 설정으로 충분히 테스트
2. ✅ Grafana 대시보드로 지표 관찰
3. ✅ 설정 변경 시 영향 측정
4. 📚 **RESILIENCE4J-TUNING-GUIDE.md** 참고
5. 🚀 프로덕션 적용 전 부하 테스트
