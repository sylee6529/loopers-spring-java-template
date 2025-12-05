#!/bin/bash

# PG 연동 및 Resilience 패턴 테스트 스크립트

echo "=== PG 연동 및 Resilience 테스트 ==="
echo ""

# 색상 코드
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

USER_ID=1

# 함수: 결과 출력
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# 1. Health Check
echo "1. Health Check"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo "   PG Simulator (8082):"
PG_HEALTH=$(curl -s http://localhost:8082/actuator/health 2>/dev/null)
if echo "$PG_HEALTH" | grep -q "UP"; then
    print_result 0 "PG Simulator is running"
else
    print_result 1 "PG Simulator is NOT running"
    echo "   Please start: ./gradlew :apps:pg-simulator:bootRun"
    exit 1
fi

echo ""
echo "   Commerce API (8080):"
API_HEALTH=$(curl -s http://localhost:8080/actuator/health 2>/dev/null)
if echo "$API_HEALTH" | grep -q "UP"; then
    print_result 0 "Commerce API is running"
else
    print_result 1 "Commerce API is NOT running"
    echo "   Please start: ./gradlew :apps:commerce-api:bootRun"
    exit 1
fi

echo ""
echo ""

# 2. 정상 결제 요청 테스트
echo "2. 정상 결제 요청 테스트"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
  -H "X-USER-ID: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "TEST-ORDER-001",
    "cardType": "SAMSUNG",
    "cardNo": "1234-5678-9012-3456",
    "amount": 5000
  }')

echo "$RESPONSE" | jq '.'

if echo "$RESPONSE" | grep -q "transactionKey"; then
    print_result 0 "결제 요청 성공"
    TRANSACTION_KEY=$(echo "$RESPONSE" | jq -r '.transactionKey')
    echo "   Transaction Key: $TRANSACTION_KEY"
else
    print_result 1 "결제 요청 실패"
fi

echo ""
echo ""

# 3. 결제 상태 조회 테스트
if [ ! -z "$TRANSACTION_KEY" ] && [ "$TRANSACTION_KEY" != "null" ]; then
    echo "3. 결제 상태 조회 테스트"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    echo "   1차 조회 (즉시):"
    STATUS_RESPONSE=$(curl -s http://localhost:8080/api/v1/payments/test/status/$TRANSACTION_KEY \
      -H "X-USER-ID: $USER_ID")

    echo "$STATUS_RESPONSE" | jq '.'
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    echo "   Status: $STATUS"

    if [ "$STATUS" = "PENDING" ]; then
        echo ""
        echo "   결제 처리 중... 3초 대기 후 재조회"
        sleep 3

        echo ""
        echo "   2차 조회 (3초 후):"
        STATUS_RESPONSE=$(curl -s http://localhost:8080/api/v1/payments/test/status/$TRANSACTION_KEY \
          -H "X-USER-ID: $USER_ID")

        echo "$STATUS_RESPONSE" | jq '.'
        STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
        echo "   Final Status: $STATUS"

        if [ "$STATUS" = "SUCCESS" ]; then
            print_result 0 "결제 성공"
        elif [ "$STATUS" = "FAILED" ]; then
            REASON=$(echo "$STATUS_RESPONSE" | jq -r '.reason')
            echo -e "${YELLOW}⚠ 결제 실패: $REASON${NC}"
        fi
    fi

    echo ""
    echo ""
fi

# 4. Circuit Breaker 테스트
echo "4. Circuit Breaker 테스트 (연속 호출로 실패율 높이기)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

SUCCESS_COUNT=0
FAIL_COUNT=0

for i in {1..10}; do
    RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/payments/test/request \
      -H "X-USER-ID: $USER_ID" \
      -H "Content-Type: application/json" \
      -d "{
        \"orderId\": \"CB-TEST-$i\",
        \"cardType\": \"SAMSUNG\",
        \"cardNo\": \"1234-5678-9012-3456\",
        \"amount\": $((5000 + i))
      }" 2>&1)

    if echo "$RESPONSE" | grep -q "transactionKey"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "   ${GREEN}[$i/10] 성공${NC}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "   ${RED}[$i/10] 실패${NC}"
    fi

    sleep 0.3
done

echo ""
echo "   결과: 성공 $SUCCESS_COUNT / 실패 $FAIL_COUNT"
FAIL_RATE=$((FAIL_COUNT * 100 / 10))
echo "   실패율: $FAIL_RATE%"

if [ $FAIL_RATE -ge 60 ]; then
    echo -e "   ${YELLOW}⚠ 실패율이 60% 이상입니다. Circuit Breaker가 OPEN될 수 있습니다.${NC}"
fi

echo ""
echo ""

# 5. Circuit Breaker 상태 확인
echo "5. Circuit Breaker 상태 확인"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

CB_STATUS=$(curl -s http://localhost:8081/actuator/circuitbreakers 2>/dev/null)

if [ ! -z "$CB_STATUS" ]; then
    echo "$CB_STATUS" | jq '.circuitBreakers.pgSimulator // {message: "Circuit Breaker 정보를 찾을 수 없습니다"}'
else
    echo "   Circuit Breaker 엔드포인트에 접근할 수 없습니다."
    echo "   actuator 설정을 확인하세요."
fi

echo ""
echo ""

# 6. Resilience4j 메트릭 확인
echo "6. Resilience4j 메트릭 확인"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

METRICS=$(curl -s http://localhost:8081/actuator/prometheus 2>/dev/null | grep -E "resilience4j_circuitbreaker_(state|calls)" | head -10)

if [ ! -z "$METRICS" ]; then
    echo "$METRICS"
    echo ""
    print_result 0 "Resilience4j 메트릭 수집 중"
else
    echo "   Resilience4j 메트릭을 찾을 수 없습니다."
fi

echo ""
echo ""

# 7. 최종 요약
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "=== 테스트 완료 ==="
echo ""
echo "추가 확인 사항:"
echo "  - Grafana Circuit Breaker 대시보드:"
echo "    http://localhost:3000/d/circuit-breaker-monitoring"
echo ""
echo "  - Actuator 엔드포인트:"
echo "    http://localhost:8081/actuator/circuitbreakers"
echo "    http://localhost:8081/actuator/circuitbreakerevents"
echo "    http://localhost:8081/actuator/retryevents"
echo ""
echo "  - 상세 가이드: PG-INTEGRATION-GUIDE.md"
