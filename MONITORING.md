# 모니터링 가이드

이 문서는 Spring Boot 애플리케이션의 모니터링 설정과 부하 테스트 방법을 설명합니다.

## 아키텍처

```
Commerce API (Spring Boot)
    ↓ (메트릭 노출: :8081/actuator/prometheus)
Prometheus (수집 및 저장)
    ↓
Grafana (시각화)
```

## 1. 모니터링 스택 시작

### 인프라 시작 (MySQL, Redis)
```bash
docker-compose -f ./docker/infra-compose.yml up -d
```

### 모니터링 스택 시작 (Prometheus, Grafana)
```bash
docker-compose -f ./docker/monitoring-compose.yml up -d
```

### Commerce API 시작
```bash
./gradlew :apps:commerce-api:bootRun
```

## 2. 연결 확인

### 자동 테스트 스크립트 실행
```bash
./test-monitoring.sh
```

이 스크립트는 다음을 확인합니다:
- ✅ Commerce API Health Check
- ✅ Actuator Prometheus 엔드포인트
- ✅ Prometheus 타겟 상태
- ✅ Grafana 연결 상태
- ✅ 샘플 메트릭 수집

### 수동 확인

#### 1) Actuator 엔드포인트 확인
```bash
# Health Check
curl http://localhost:8080/actuator/health

# Prometheus 메트릭 (관리 포트)
curl http://localhost:8081/actuator/prometheus

# 메트릭 목록
curl http://localhost:8081/actuator/metrics
```

#### 2) Prometheus UI
브라우저에서 접속: http://localhost:9090

**확인 사항:**
- Status > Targets에서 `spring-boot-app` job이 **UP** 상태인지 확인
- Graph에서 쿼리 테스트:
  ```promql
  jvm_memory_used_bytes{application="commerce-api"}
  http_server_requests_seconds_count{application="commerce-api"}
  ```

#### 3) Grafana UI
브라우저에서 접속: http://localhost:3000
- **로그인:** admin / admin
- **데이터소스 확인:** Configuration > Data Sources > Prometheus (초록색 체크)
- **대시보드:** Dashboards > Browse

## 3. 대시보드

### Spring Boot - Load Test Dashboard
부하 테스트용 주요 메트릭을 표시합니다.

**URL:** http://localhost:3000/d/spring-boot-load-test

**패널:**
1. **HTTP Requests Rate** - 초당 요청 처리율
2. **HTTP Response Time** - p50, p95, p99 응답 시간
3. **JVM Heap Memory** - 힙 메모리 사용량
4. **GC Time** - 가비지 컬렉션 시간
5. **Tomcat Threads** - 톰캣 워커 스레드 상태
6. **HikariCP Connection Pool** - DB 커넥션 풀 상태
7. **JVM Thread States** - JVM 스레드 상태 분포
8. **CPU Usage** - 프로세스 및 시스템 CPU 사용률

### Circuit Breaker Monitoring
서킷브레이커 상태와 메트릭을 표시합니다. (Resilience4j 적용 시)

**URL:** http://localhost:3000/d/circuit-breaker-monitoring

**패널:**
1. **Circuit Breaker State** - CLOSED/OPEN/HALF_OPEN 상태
2. **Call Rate** - 성공/실패 호출 비율
3. **Failure & Slow Call Rate** - 실패율과 느린 호출 비율
4. **Rejected Calls** - 서킷 오픈으로 인한 거부된 호출

## 4. 부하 테스트

### 간단한 테스트 (내장 스크립트)
```bash
# 기본: 100개 요청, 동시성 10
./simple-load-test.sh

# 커스텀: 1000개 요청, 동시성 50
./simple-load-test.sh 1000 50
```

이 스크립트는:
- 상품 목록 조회 API에 부하를 생성
- 실시간으로 메트릭 수집 상태 확인
- 요청 처리율, 응답 시간, JVM/스레드 상태 출력

### Apache Bench (ab)
```bash
# 설치 (macOS)
brew install apache2

# 테스트 실행 (1000개 요청, 동시성 100)
ab -n 1000 -c 100 -H "X-USER-ID: 1" http://localhost:8080/api/v1/products?offset=0&limit=10
```

### wrk (권장)
```bash
# 설치 (macOS)
brew install wrk

# 테스트 실행 (10개 스레드, 100개 커넥션, 30초)
wrk -t10 -c100 -d30s -H "X-USER-ID: 1" http://localhost:8080/api/v1/products?offset=0&limit=10
```

### k6 (고급)
```bash
# 설치 (macOS)
brew install k6

# 예제 스크립트 (loadtest.js)
cat > loadtest.js << 'EOF'
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 50,           // 가상 유저 50명
  duration: '30s',   // 30초 동안
};

export default function () {
  const params = {
    headers: { 'X-USER-ID': '1' },
  };

  const res = http.get('http://localhost:8080/api/v1/products?offset=0&limit=10', params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 200ms': (r) => r.timings.duration < 200,
  });

  sleep(0.1);
}
EOF

# 실행
k6 run loadtest.js
```

## 5. 수집되는 메트릭

### HTTP 메트릭
- `http_server_requests_seconds_count` - 요청 수
- `http_server_requests_seconds_sum` - 총 응답 시간
- `http_server_requests_seconds_bucket` - 히스토그램 (p50, p95, p99 계산용)

### JVM 메트릭
- `jvm_memory_used_bytes` - 메모리 사용량 (heap/non-heap)
- `jvm_memory_max_bytes` - 최대 메모리
- `jvm_gc_pause_seconds_*` - GC 일시정지 시간
- `jvm_threads_*` - 스레드 상태

### Tomcat 메트릭
- `tomcat_threads_current_threads` - 현재 스레드 수
- `tomcat_threads_busy_threads` - 사용 중인 스레드 수
- `tomcat_threads_config_max_threads` - 최대 스레드 수
- `tomcat_sessions_*` - 세션 메트릭

### HikariCP 메트릭
- `hikaricp_connections_active` - 활성 커넥션
- `hikaricp_connections_idle` - 유휴 커넥션
- `hikaricp_connections_max` - 최대 커넥션
- `hikaricp_connections_pending` - 대기 중인 요청

### 시스템 메트릭
- `process_cpu_usage` - 프로세스 CPU 사용률
- `system_cpu_usage` - 시스템 CPU 사용률
- `process_uptime_seconds` - 가동 시간

### Circuit Breaker 메트릭 (Resilience4j)
- `resilience4j_circuitbreaker_state` - 서킷브레이커 상태 (0:CLOSED, 1:OPEN, 2:HALF_OPEN)
- `resilience4j_circuitbreaker_calls_seconds_*` - 호출 통계
- `resilience4j_circuitbreaker_failure_rate` - 실패율
- `resilience4j_circuitbreaker_slow_call_rate` - 느린 호출 비율

## 6. 트러블슈팅

### Prometheus에서 타겟이 DOWN 상태
```bash
# Commerce API가 실행 중인지 확인
curl http://localhost:8081/actuator/prometheus

# Docker 네트워크 확인 (host.docker.internal이 동작하는지)
docker exec -it monitoring-prometheus-1 ping host.docker.internal
```

### Grafana에서 데이터가 안 보임
1. Prometheus 데이터소스 연결 확인
   - Configuration > Data Sources > Prometheus > Test
2. Prometheus에서 메트릭이 수집되는지 확인
   - http://localhost:9090 > Graph > 쿼리 실행
3. 시간 범위 확인
   - Grafana 우측 상단의 시간 선택기에서 "Last 15 minutes" 선택

### 메트릭이 수집되지 않음
```bash
# Actuator 엔드포인트가 활성화되어 있는지 확인
curl http://localhost:8081/actuator | jq '.[]'

# monitoring.yml이 import되어 있는지 확인
grep "monitoring.yml" apps/commerce-api/src/main/resources/application.yml

# 로그 확인
./gradlew :apps:commerce-api:bootRun | grep -i "actuator\|prometheus"
```

## 7. 메트릭 추가하기

### 커스텀 메트릭 예제
```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@Service
public class OrderService {
    private final Counter orderCounter;

    public OrderService(MeterRegistry meterRegistry) {
        this.orderCounter = Counter.builder("orders.placed")
            .description("Number of orders placed")
            .tag("status", "success")
            .register(meterRegistry);
    }

    public void placeOrder(Order order) {
        // 주문 처리 로직
        orderCounter.increment();
    }
}
```

### Prometheus 쿼리
```promql
# 초당 주문 수
rate(orders_placed_total[1m])

# 총 주문 수
orders_placed_total
```

## 8. 참고 링크

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)
- [Micrometer](https://micrometer.io/docs)
- [Prometheus](https://prometheus.io/docs/)
- [Grafana](https://grafana.com/docs/)
- [Resilience4j Metrics](https://resilience4j.readme.io/docs/micrometer)
