# Resilience4j 설정 튜닝 가이드

## 1. PG Simulator 서비스 특성 분석

### 📊 PG Simulator의 동작 특성 (README 기준)

```
요청 단계:
├─ 성공 확률: 60%
├─ 실패 확률: 40% (서버 불안정 응답)
└─ 응답 지연: 100ms ~ 500ms (랜덤)

처리 단계 (비동기):
├─ 처리 지연: 1s ~ 5s
└─ 처리 결과:
    ├─ 성공: 70%
    ├─ 한도 초과: 20%
    └─ 잘못된 카드: 10%
```

### 🎯 핵심 특성 요약

| 특성 | 값 | 영향 |
|------|-----|------|
| **정상 실패율** | 40% | Circuit Breaker 임계값 결정 |
| **정상 응답 시간** | 100-500ms | Timeout, Slow Call 기준 |
| **비동기 처리** | 1-5초 | 콜백/폴링 전략 필요 |
| **트랜잭션성** | 금융 거래 | Retry 신중하게 결정 |

---

## 2. Resilience4j 설정 결정 시 고려사항

### 🔴 Circuit Breaker 설정

#### 1) Sliding Window Type

**선택지:**
- `COUNT_BASED`: 최근 N개 호출 기준
- `TIME_BASED`: 최근 N초간 호출 기준

**고려사항:**

| 항목 | COUNT_BASED | TIME_BASED |
|------|-------------|------------|
| **적합한 경우** | 트래픽이 일정한 경우 | 트래픽 변동이 큰 경우 |
| **장점** | 명확한 샘플 수 | 시간 기준으로 일관성 |
| **단점** | 저트래픽 시 반응 느림 | 저트래픽 시 샘플 부족 |

**PG Simulator 추천: `COUNT_BASED`**
- 이유: 결제는 트랜잭션 단위로 발생
- PG의 40% 실패율을 정확히 측정 가능
- 트래픽이 일정하지 않더라도 N개 호출 후 판단

#### 2) Sliding Window Size

**고려사항:**
```
- 너무 작으면: 일시적 오류에 민감 (false positive)
- 너무 크면: 장애 감지가 느림 (false negative)
```

**계산 방법:**

PG의 정상 실패율이 40%일 때:
```
만약 실패율 임계값 = 60%라면,
최소 몇 개 샘플이 필요한가?

예시:
- Window Size = 5: 3개 실패 → 60% (민감함)
- Window Size = 10: 6개 실패 → 60% (적절)
- Window Size = 20: 12개 실패 → 60% (둔감함)
```

**PG Simulator 추천: `10`**
- 이유: 통계적으로 의미있는 최소 샘플
- 40% vs 60% 구분 가능

#### 3) Minimum Number of Calls

**고려사항:**
```
- 목적: 충분한 샘플 확보 후 판단
- Window Size보다 작거나 같아야 함
```

**PG Simulator 추천: `5`**
- 이유: Window Size(10)의 절반
- 빠른 장애 감지 + 충분한 샘플

#### 4) Failure Rate Threshold

**고려사항:**
```
정상 실패율 < Threshold < 비정상 실패율

PG의 경우:
- 정상 실패율: 40%
- 일시적 장애: 50-60%
- 심각한 장애: 70%+
```

**계산:**
```
Threshold = 정상 실패율 + 안전 마진

예시:
- 50%: 너무 민감 (정상 변동도 OPEN)
- 60%: 적절 (명확한 장애만 OPEN)
- 70%: 너무 둔감 (심각해진 후 OPEN)
```

**PG Simulator 추천: `60%`**
- 이유: 정상 40% + 20% 마진
- 명확한 장애 상황만 Circuit OPEN

#### 5) Slow Call Rate Threshold & Duration

**고려사항:**
```
정상 응답 시간: 100-500ms
평균: ~300ms
```

**Slow Call Duration 설정:**
```
Duration = P95 응답 시간 × 2

예시:
- 500ms: 너무 짧음 (정상도 slow로 판단)
- 1000ms: 적절 (명확히 느린 경우만)
- 2000ms: 너무 김 (장애 감지 늦음)
```

**PG Simulator 추천:**
- `slowCallDurationThreshold: 1s`
- `slowCallRateThreshold: 50%`

#### 6) Wait Duration in Open State

**고려사항:**
```
- 너무 짧으면: 복구 전 재시도 → 부하 증가
- 너무 길면: 복구 후에도 OPEN 유지 → 서비스 저하
```

**일반적 기준:**
```
일시적 장애 (네트워크): 5-10초
시스템 재시작: 30-60초
DB 장애: 60-120초
```

**PG Simulator 추천: `15s`**
- 이유: PG의 일시적 불안정 고려
- 너무 자주 재시도하지 않음

#### 7) Permitted Calls in Half-Open

**고려사항:**
```
- HALF_OPEN에서 테스트할 호출 수
- 너무 적으면: 우연히 성공/실패 가능
- 너무 많으면: 복구 중인 시스템에 부하
```

**PG Simulator 추천: `3`**
- 이유: 최소한의 검증 (성공률 60%이므로)

---

### 🔄 Retry 설정

#### 1) Max Attempts

**고려사항:**
```
금융 거래의 특성:
- 멱등성(Idempotency) 보장 필요
- 중복 결제 위험
- PG 부하 고려
```

**계산:**
```
총 시도 = 원본 1회 + Retry (N-1)회

예시:
- maxAttempts = 1: Retry 없음
- maxAttempts = 2: 1번 재시도
- maxAttempts = 3: 2번 재시도
```

**PG Simulator 추천: `2` (총 2회 시도)**
- 이유:
  - 40% 실패율에서 2번 시도 → 성공 확률 84%
  - 중복 결제 위험 최소화
  - PG 부하 적절

**수식:**
```
1회 성공률 = 60%
2회 연속 실패 확률 = 0.4 × 0.4 = 16%
2회 중 1회 이상 성공 = 1 - 0.16 = 84%
```

#### 2) Wait Duration

**고려사항:**
```
- 네트워크 일시 장애: 100-300ms
- 서버 부하: 500-1000ms
- 시스템 재시작: 수 초
```

**PG Simulator 추천: `300ms`**
- 이유: PG의 응답 시간(100-500ms) 고려

#### 3) Exponential Backoff

**고려사항:**
```
활성화 시:
- 1차 재시도: waitDuration
- 2차 재시도: waitDuration × multiplier
- 3차 재시도: waitDuration × multiplier²

장점: 시스템 복구 시간 제공
단점: 응답 시간 증가
```

**PG Simulator 추천: `true`, multiplier `2`**
- 1차: 300ms
- 2차: 600ms
- 이유: PG 부하 분산

#### 4) Retry Exceptions

**고려사항:**
```
재시도 해야 할 예외:
- TimeoutException (일시적)
- ResourceAccessException (네트워크)
- HttpServerErrorException (5xx)

재시도 하면 안 되는 예외:
- IllegalArgumentException (잘못된 요청)
- HttpClientErrorException (4xx)
- CoreException (비즈니스 로직 오류)
```

---

### ⏱️ TimeLimiter 설정

#### Timeout Duration

**고려사항:**
```
정상 응답 시간 + 재시도 시간 + 여유

계산:
- 정상 응답: 100-500ms (평균 300ms)
- 재시도 1회: 300ms (wait) + 300ms (요청) = 600ms
- 총 예상: 300 + 600 = 900ms
- 여유: 2배 = 1800ms ≈ 2s
```

**PG Simulator 추천: `2s`**
- 이유: 정상 + 재시도 시나리오 커버

---

## 3. 종합 권장 설정 (PG Simulator 최적화)

### 📋 최종 설정 매트릭스

| 설정 | 현재 값 | 최적 값 | 이유 |
|------|---------|---------|------|
| **Circuit Breaker** |
| slidingWindowType | COUNT_BASED | ✅ COUNT_BASED | 트랜잭션 기반 |
| slidingWindowSize | 10 | ✅ 10 | 통계적 유의성 |
| minimumNumberOfCalls | 5 | ✅ 5 | 빠른 감지 |
| failureRateThreshold | 60% | ✅ 60% | 정상(40%) + 마진 |
| slowCallDurationThreshold | 1s | ✅ 1s | P95 × 2 |
| slowCallRateThreshold | 50% | ✅ 50% | 절반 이상 느림 |
| waitDurationInOpenState | 15s | ✅ 15s | 복구 대기 |
| permittedNumberOfCallsInHalfOpenState | 3 | ✅ 3 | 최소 검증 |
| **Retry** |
| maxAttempts | 2 | ✅ 2 | 중복 방지 |
| waitDuration | 300ms | ✅ 300ms | 응답 시간 기준 |
| exponentialBackoff | true | ✅ true | 부하 분산 |
| exponentialBackoffMultiplier | 2 | ✅ 2 | 표준 배수 |
| **TimeLimiter** |
| timeoutDuration | 2s | ✅ 2s | 정상 + 재시도 |

---

## 4. 모니터링 지표

### 🎯 주요 KPI (Key Performance Indicators)

#### 1) Circuit Breaker 상태 지표

| 지표 | 의미 | 목표 | 경고 |
|------|------|------|------|
| `resilience4j_circuitbreaker_state` | 0:CLOSED, 1:OPEN, 2:HALF_OPEN | CLOSED | OPEN |
| `resilience4j_circuitbreaker_failure_rate` | 실패율 (%) | < 40% | > 60% |
| `resilience4j_circuitbreaker_slow_call_rate` | 느린 호출 비율 (%) | < 10% | > 50% |
| `resilience4j_circuitbreaker_buffered_calls` | 버퍼된 호출 수 | 10개 | < 5개 |

#### 2) 호출 결과 지표

| 지표 | 의미 | PromQL |
|------|------|--------|
| **성공 호출** | 성공한 요청 수 | `resilience4j_circuitbreaker_calls_seconds_count{kind="successful"}` |
| **실패 호출** | 실패한 요청 수 | `resilience4j_circuitbreaker_calls_seconds_count{kind="failed"}` |
| **거부 호출** | Circuit OPEN으로 거부된 요청 | `resilience4j_circuitbreaker_calls_seconds_count{kind="not_permitted"}` |

#### 3) 성능 지표

| 지표 | 의미 | 목표 |
|------|------|------|
| **평균 응답 시간** | 전체 응답 시간 평균 | < 500ms |
| **P95 응답 시간** | 95% 요청의 응답 시간 | < 1s |
| **P99 응답 시간** | 99% 요청의 응답 시간 | < 2s |

#### 4) Retry 지표

| 지표 | 의미 | PromQL |
|------|------|--------|
| **재시도 성공** | 재시도 후 성공 | `resilience4j_retry_calls_seconds_count{kind="successful_with_retry"}` |
| **재시도 실패** | 재시도 후 실패 | `resilience4j_retry_calls_seconds_count{kind="failed_with_retry"}` |
| **재시도 없음** | 재시도 없이 성공 | `resilience4j_retry_calls_seconds_count{kind="successful_without_retry"}` |

---

## 5. 현재 설정 확인

### ✅ 이미 설정된 것

1. **Prometheus 수집**
   - ✅ `resilience4j-spring-boot3` 의존성 (자동 메트릭 노출)
   - ✅ Actuator `/actuator/prometheus` 엔드포인트
   - ✅ Prometheus 스크래핑 설정

2. **Circuit Breaker 대시보드**
   - ✅ `circuit-breaker.json` 대시보드
   - ⚠️ 개선 필요 (더 많은 지표)

### ⚠️ 추가 필요한 것

1. **더 상세한 대시보드**
   - Retry 지표
   - 응답 시간 분포
   - 시간대별 Circuit 상태 변화

2. **Alert 설정**
   - Circuit OPEN 알림
   - 실패율 임계값 알림

---

## 6. Grafana 대시보드 설정

### 📊 개선된 대시보드 구성

#### Row 1: Circuit Breaker 상태 (Overview)
- **Circuit State Gauge**: 현재 상태 (CLOSED/OPEN/HALF_OPEN)
- **Failure Rate Gauge**: 현재 실패율
- **Slow Call Rate Gauge**: 느린 호출 비율

#### Row 2: 호출 통계 (Call Statistics)
- **Call Rate Graph**: 성공/실패/거부 호출 비율 (시간별)
- **Success Rate %**: 성공률 퍼센트 (시간별)

#### Row 3: 응답 시간 (Response Time)
- **Response Time Percentiles**: p50, p95, p99
- **Slow Calls Count**: 느린 호출 수 추이

#### Row 4: Retry 분석 (Retry Analysis)
- **Retry Attempts**: 재시도 횟수 분포
- **Retry Success Rate**: 재시도 성공률

#### Row 5: 상태 전환 이력 (State Transitions)
- **Circuit State Timeline**: 시간대별 상태 변화
- **State Change Events**: 상태 전환 이벤트 로그

---

## 7. 실습 단계별 가이드

### 🎓 Step 1: 기본 동작 확인

**목표**: Circuit Breaker가 동작하는지 확인

**테스트:**
```bash
# 1. 정상 요청 (성공률 60%)
for i in {1..20}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
    -H "X-USER-ID: 1" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"TEST-$i\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-5678-9012-3456\", \"amount\": 5000}"
  echo ""
  sleep 0.5
done
```

**확인 지표:**
- `resilience4j_circuitbreaker_buffered_calls` → 20
- `resilience4j_circuitbreaker_failure_rate` → ~40%
- `resilience4j_circuitbreaker_state` → 0 (CLOSED)

---

### 🎓 Step 2: Circuit OPEN 트리거

**목표**: 실패율 60% 초과 시 OPEN 확인

**테스트:**
```bash
# 연속 호출하여 실패율 높이기
./test-pg-resilience.sh
```

**확인 지표:**
- `resilience4j_circuitbreaker_failure_rate` → 60% 이상
- `resilience4j_circuitbreaker_state` → 1 (OPEN)
- `resilience4j_circuitbreaker_calls{kind="not_permitted"}` 증가

**Grafana에서 확인:**
- Circuit State Gauge가 빨간색(OPEN)으로 변경
- Call Rate Graph에서 거부된 호출 증가

---

### 🎓 Step 3: 복구 과정 관찰

**목표**: OPEN → HALF_OPEN → CLOSED 전환 확인

**테스트:**
```bash
# 1. Circuit OPEN 후 15초 대기
echo "Waiting for 15 seconds..."
sleep 15

# 2. 테스트 호출 (HALF_OPEN)
curl -s http://localhost:8080/api/v1/payments/test/request \
  -H "X-USER-ID: 1" \
  -H "Content-Type: application/json" \
  -d '{"orderId": "RECOVERY-1", "cardType": "SAMSUNG", "cardNo": "1234-5678-9012-3456", "amount": 5000}'
```

**확인 지표:**
- 15초 후: `state` → 2 (HALF_OPEN)
- 3번 성공 후: `state` → 0 (CLOSED)

---

### 🎓 Step 4: Slow Call 감지

**목표**: 느린 응답 감지 확인

**현재 제약**: PG Simulator는 100-500ms 응답이므로 Slow Call이 거의 발생하지 않음

**개선 방법** (옵션):
```yaml
# resilience4j.yml 수정 (테스트용)
slowCallDurationThreshold: 200ms  # 낮춰서 테스트
```

---

### 🎓 Step 5: Retry 동작 확인

**목표**: 재시도 로직 확인

**확인 방법:**
```bash
# 로그에서 재시도 확인
tail -f logs/application.log | grep -E "PG|Retry"
```

**확인 지표:**
- `resilience4j_retry_calls{kind="successful_with_retry"}` 증가
- 로그에서 "[PG] 결제 요청 시작" 2회 출력

---

## 8. 설정 최적화 실습

### 🔬 실험 1: Failure Rate Threshold 조정

**가설**: Threshold를 50%로 낮추면 Circuit이 더 자주 OPEN될 것

**변경:**
```yaml
failureRateThreshold: 50  # 60 → 50
```

**테스트 후 비교:**
- Circuit OPEN 빈도
- 거부된 호출 수
- 전체 성공률

---

### 🔬 실험 2: Retry 횟수 증가

**가설**: maxAttempts를 3으로 늘리면 성공률이 증가할 것

**변경:**
```yaml
maxAttempts: 3  # 2 → 3
```

**계산:**
```
3회 연속 실패 확률 = 0.4³ = 6.4%
성공률 = 93.6% (vs 기존 84%)
```

**트레이드오프:**
- ✅ 성공률 9.6% 증가
- ❌ PG 부하 50% 증가
- ❌ 평균 응답 시간 증가

---

### 🔬 실험 3: Window Size 조정

**가설**: Window Size를 5로 줄이면 더 빠르게 감지할 것

**변경:**
```yaml
slidingWindowSize: 5  # 10 → 5
```

**예상 결과:**
- ✅ 빠른 장애 감지
- ❌ 일시적 오류에 민감 (false positive 증가)

---

## 9. 체크리스트

### ✅ 설정 결정 체크리스트

- [ ] PG Simulator 특성 분석 완료
- [ ] Circuit Breaker 타입 결정 (COUNT vs TIME)
- [ ] Sliding Window Size 계산
- [ ] Failure Rate Threshold 결정
- [ ] Slow Call 기준 설정
- [ ] Retry 횟수 결정
- [ ] Timeout 시간 계산
- [ ] 트레이드오프 고려

### ✅ 모니터링 준비 체크리스트

- [ ] Prometheus 메트릭 수집 확인
- [ ] Grafana 대시보드 생성
- [ ] 주요 KPI 정의
- [ ] Alert 설정 (옵션)

### ✅ 테스트 체크리스트

- [ ] 정상 동작 확인
- [ ] Circuit OPEN 트리거 확인
- [ ] 복구 과정 관찰
- [ ] Retry 동작 확인
- [ ] Slow Call 감지 확인

---

## 10. 다음 단계

### 📚 학습 순서

1. **현재 설정으로 테스트** → 동작 이해
2. **Grafana 대시보드 확인** → 지표 관찰
3. **설정 하나씩 변경** → 영향 분석
4. **최적 설정 도출** → 프로덕션 적용

### 🎯 최종 목표

서비스 특성에 맞는 Resilience4j 설정을 데이터 기반으로 결정하고,
지표를 통해 지속적으로 모니터링하고 개선하는 능력 습득
