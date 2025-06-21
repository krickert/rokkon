#!/bin/bash
# Script to test CLI registration with test-module

echo "=== Testing CLI Registration Flow ==="

# Step 1: Start test-module
echo "1. Starting test-module on port 49095..."
cd modules/test-module
./gradlew quarkusDev -Dquarkus.http.port=39095 -Dquarkus.grpc.server.port=49095 > test-module.log 2>&1 &
TEST_MODULE_PID=$!
cd ../..

echo "   Waiting for test-module to start (15 seconds)..."
sleep 15

# Step 2: Test if module is running
echo "2. Testing if test-module is responding..."
if command -v grpcurl &> /dev/null; then
    echo "   Using grpcurl to test connection..."
    grpcurl -plaintext localhost:49095 list
    
    echo "   Testing GetServiceRegistration..."
    grpcurl -plaintext -d '{}' localhost:49095 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration
else
    echo "   grpcurl not installed, skipping gRPC tests"
fi

# Step 3: Build the CLI
echo "3. Building CLI..."
./gradlew :cli:build -x test

# Step 4: Run CLI in different modes
echo "4. Testing CLI --help..."
cd cli
java -jar build/quarkus-app/quarkus-run.jar --help

echo ""
echo "5. Testing CLI register --help..."
java -jar build/quarkus-app/quarkus-run.jar register --help

# Note: We can't test actual registration without the engine running
echo ""
echo "6. To test actual registration, you would run:"
echo "   java -jar build/quarkus-app/quarkus-run.jar register \\"
echo "     --module-host localhost \\"
echo "     --module-port 49095 \\"
echo "     --engine-host <engine-host> \\"
echo "     --engine-port 49000"

cd ..

# Cleanup
echo ""
echo "7. Stopping test-module..."
kill $TEST_MODULE_PID 2>/dev/null

echo ""
echo "=== Test Complete ==="
echo "Check test-module.log for module output"