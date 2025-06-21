#!/bin/bash
# Simple test to verify CLI can connect to test-module

echo "=== Testing CLI Local Connection ==="

# Build everything first
echo "Building modules..."
./gradlew :modules:test-module:build :cli:build -x test

# Start test-module in background
echo "Starting test-module..."
cd /home/krickert/IdeaProjects/rokkon/rokkon-engine/rokkon-engine-fix-structure-branch/modules/test-module
nohup ./gradlew quarkusDev -Dquarkus.http.port=39095 -Dquarkus.grpc.server.port=49095 > test-module.log 2>&1 &
TEST_PID=$!
cd ../..

echo "Waiting for test-module to start (20 seconds)..."
sleep 20

# Check if module is running
echo "Checking if test-module is running..."
if lsof -i:49095 > /dev/null 2>&1; then
    echo "✓ Test-module is listening on port 49095"
else
    echo "✗ Test-module is not running on port 49095"
    echo "Check modules/test-module/test-module.log for errors"
    kill $TEST_PID 2>/dev/null
    exit 1
fi

# Test with grpcurl if available
if command -v grpcurl &> /dev/null; then
    echo ""
    echo "Testing gRPC connection with grpcurl..."
    grpcurl -plaintext localhost:49095 list
    
    echo ""
    echo "Getting service registration info..."
    grpcurl -plaintext -d '{}' localhost:49095 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration
fi

# Now test the CLI (it will fail to connect to engine, but should connect to module)
echo ""
echo "Testing CLI connection to module (will fail at engine connection)..."
timeout 10 java -jar /home/krickert/IdeaProjects/rokkon/rokkon-engine/rokkon-engine-fix-structure-branch/cli/build/quarkus-app/quarkus-run.jar register \
    --module-host localhost \
    --module-port 49095 \
    --engine-host localhost \
    --engine-port 49000 \
    --verbose || echo "Expected failure (no engine running)"

# Cleanup
echo ""
echo "Stopping test-module..."
kill $TEST_PID 2>/dev/null

echo ""
echo "=== Test Complete ==="
echo "Check modules/test-module/test-module.log for module output"