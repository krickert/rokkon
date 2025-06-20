#!/bin/bash

# Script to test gRPC endpoints
# Usage: ./test-grpc.sh [host] [port]

HOST="${1:-localhost}"
PORT="${2:-9000}"

echo "Testing gRPC endpoints on ${HOST}:${PORT}"
echo "========================================="

# Test gRPC health check
echo -e "\n1. Testing gRPC Health Check:"
grpcurl -plaintext "${HOST}:${PORT}" grpc.health.v1.Health/Check

# List available services
echo -e "\n2. Listing available services:"
grpcurl -plaintext "${HOST}:${PORT}" list

# Describe services if reflection is enabled
echo -e "\n3. Describing available services:"
for service in $(grpcurl -plaintext "${HOST}:${PORT}" list 2>/dev/null); do
    echo -e "\n   Service: $service"
    grpcurl -plaintext "${HOST}:${PORT}" describe "$service"
done

echo -e "\n========================================="
echo "gRPC endpoint tests completed"