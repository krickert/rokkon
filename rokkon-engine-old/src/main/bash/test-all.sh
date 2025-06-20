#!/bin/bash

# Script to test all endpoints (HTTP and gRPC)
# Usage: ./test-all.sh

HTTP_HOST="${1:-localhost}"
HTTP_PORT="${2:-8081}"
GRPC_PORT="${3:-9000}"

echo "Rokkon Engine Endpoint Tests"
echo "============================"
echo "HTTP: http://${HTTP_HOST}:${HTTP_PORT}"
echo "gRPC: ${HTTP_HOST}:${GRPC_PORT}"
echo

# Test HTTP endpoints
echo "HTTP Endpoints:"
echo "---------------"

echo -n "1. Ping endpoint: "
response=$(curl -s -w "\n[HTTP %{http_code}]" "http://${HTTP_HOST}:${HTTP_PORT}/ping")
echo "$response"

echo -n "2. Health endpoint: "
health=$(curl -s "http://${HTTP_HOST}:${HTTP_PORT}/q/health")
status=$(echo "$health" | jq -r '.status' 2>/dev/null || echo "Failed to parse")
echo "Status: $status"
echo "$health" | jq '.' 2>/dev/null || echo "$health"

# Test gRPC endpoints
echo -e "\ngRPC Endpoints:"
echo "---------------"

echo "1. gRPC Health Check:"
grpcurl -plaintext "${HTTP_HOST}:${GRPC_PORT}" grpc.health.v1.Health/Check 2>&1 | sed 's/^/   /'

echo -e "\n2. Available gRPC services:"
services=$(grpcurl -plaintext "${HTTP_HOST}:${GRPC_PORT}" list 2>&1)
if [[ $? -eq 0 ]]; then
    echo "$services" | sed 's/^/   /'
else
    echo "   Reflection not enabled or no services available"
fi

echo -e "\n============================"
echo "Tests completed"