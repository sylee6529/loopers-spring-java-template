#!/bin/bash

# 간단한 부하 테스트 스크립트 (메트릭 수집 확인용)

echo "=== 간단한 부하 테스트 시작 ==="
echo ""
echo "이 스크립트는 API에 요청을 보내서 메트릭이 제대로 수집되는지 확인합니다."
echo "Grafana에서 실시간으로 메트릭을 확인하세요: http://localhost:3000"
echo ""

# 사용자 ID 설정 (X-USER-ID 헤더용)
USER_ID=1

# API Base URL
API_URL="http://localhost:8080"

# 요청 횟수 설정
REQUESTS=${1:-100}
CONCURRENT=${2:-10}

echo "설정:"
echo "  - 총 요청 수: $REQUESTS"
echo "  - 동시 요청 수: $CONCURRENT"
echo "  - User ID: $USER_ID"
echo ""

# 상품 목록 조회 부하 테스트
echo "1. 상품 목록 조회 부하 테스트 시작..."
for i in $(seq 1 $REQUESTS); do
  curl -s -X GET "$API_URL/api/v1/products?offset=0&limit=10" \
    -H "X-USER-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -o /dev/null &

  # 동시 요청 수 제어
  if [ $((i % CONCURRENT)) -eq 0 ]; then
    wait
    echo "  Progress: $i/$REQUESTS requests sent"
  fi
done
wait
echo "  완료: $REQUESTS 요청 전송"
echo ""

# 잠시 대기 (메트릭 수집을 위해)
echo "메트릭 수집 대기 중... (5초)"
sleep 5
echo ""

# 결과 확인
echo "2. 메트릭 확인 (최근 1분간)"
echo ""

echo "  - HTTP 요청 처리율:"
curl -s 'http://localhost:9090/api/v1/query?query=rate(http_server_requests_seconds_count{application="commerce-api",uri="/api/v1/products"}[1m])' \
  | jq -r '.data.result[] | "    " + .metric.method + " " + .metric.uri + " (status: " + .metric.status + "): " + .value[1] + " req/sec"'
echo ""

echo "  - 응답 시간 (p95):"
curl -s 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{application="commerce-api",uri="/api/v1/products"}[1m]))' \
  | jq -r '.data.result[] | "    " + .metric.method + " " + .metric.uri + ": " + (.value[1] | tonumber * 1000 | tostring) + " ms"'
echo ""

echo "  - JVM Heap 사용량:"
curl -s 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes{application="commerce-api",area="heap"}' \
  | jq -r '.data.result[] | "    " + .metric.id + ": " + ((.value[1] | tonumber / 1024 / 1024) | tostring) + " MB"'
echo ""

echo "  - Tomcat 스레드 상태:"
curl -s 'http://localhost:9090/api/v1/query?query=tomcat_threads_busy_threads{application="commerce-api"}' \
  | jq -r '.data.result[] | "    Busy Threads: " + .value[1]'
curl -s 'http://localhost:9090/api/v1/query?query=tomcat_threads_current_threads{application="commerce-api"}' \
  | jq -r '.data.result[] | "    Current Threads: " + .value[1]'
echo ""

echo "=== 부하 테스트 완료 ==="
echo ""
echo "Grafana 대시보드에서 상세 메트릭을 확인하세요:"
echo "  - Spring Boot Load Test: http://localhost:3000/d/spring-boot-load-test"
echo "  - Circuit Breaker: http://localhost:3000/d/circuit-breaker-monitoring"
echo ""
echo "더 강력한 부하 테스트를 위해서는 다음 도구들을 사용하세요:"
echo "  - Apache Bench (ab): ab -n 1000 -c 10 http://localhost:8080/api/v1/products"
echo "  - wrk: wrk -t10 -c100 -d30s http://localhost:8080/api/v1/products"
echo "  - k6: k6 run loadtest.js"
