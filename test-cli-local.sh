#!/bin/bash
# Test script to verify CLI can connect to test module locally

echo "Starting test-module in dev mode..."
cd modules/test-module
./gradlew quarkusDev -Dquarkus.http.port=39095 -Dquarkus.grpc.server.port=49095 &
TEST_MODULE_PID=$!

echo "Waiting for test-module to start..."
sleep 10

echo "Testing gRPC connection to test-module..."
grpcurl -plaintext localhost:49095 list

echo "Testing GetServiceRegistration..."
grpcurl -plaintext -d '{}' localhost:49095 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration

echo "Stopping test-module..."
kill $TEST_MODULE_PID

echo "Test complete!"