#!/bin/bash

# 모니터링 연결 테스트 스크립트

echo "=== 모니터링 연결 테스트 ==="
echo ""

# 1. Commerce API Health Check
echo "1. Commerce API Health Check (http://localhost:8080/actuator/health)"
curl -s http://localhost:8080/actuator/health | jq '.'
echo ""

# 2. Actuator Prometheus Endpoint
echo "2. Actuator Prometheus Endpoint (http://localhost:8081/actuator/prometheus)"
echo "Available metrics:"
curl -s http://localhost:8081/actuator/prometheus | head -20
echo "..."
echo "(Showing first 20 lines only)"
echo ""

# 3. Prometheus Targets
echo "3. Prometheus Targets Status (http://localhost:9090/api/v1/targets)"
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health, lastError: .lastError}'
echo ""

# 4. Grafana Health
echo "4. Grafana Health (http://localhost:3000/api/health)"
curl -s http://localhost:3000/api/health | jq '.'
echo ""

# 5. Sample Metrics Check
echo "5. Sample Metrics Check (via Prometheus)"
echo "   - JVM Memory:"
curl -s 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes{application="commerce-api"}' | jq '.data.result[] | {metric: .metric.id, value: .value[1]}'
echo ""
echo "   - HTTP Requests:"
curl -s 'http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count{application="commerce-api"}' | jq '.data.result[] | {uri: .metric.uri, method: .metric.method, count: .value[1]}'
echo ""

echo "=== 테스트 완료 ==="
echo ""
echo "추가 확인 사항:"
echo "- Grafana UI: http://localhost:3000 (admin/admin)"
echo "- Prometheus UI: http://localhost:9090"
echo "- Actuator Endpoints: http://localhost:8081/actuator"
