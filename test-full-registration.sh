#!/bin/bash
# Test full registration flow with engine running

echo "=== Testing Full Module Registration Flow ==="

# Check if engine is running
echo "1. Checking if engine is running on port 49000..."
if ! lsof -i:49000 > /dev/null 2>&1; then
    echo "✗ Engine is not running on port 49000"
    echo "Please start the engine with: ./gradlew :rokkon-engine:quarkusDev"
    exit 1
fi
echo "✓ Engine is running"

# Engine already has the streaming registration service built-in
echo ""
echo "2. Engine contains the streaming registration service"

# Build everything
echo ""
echo "3. Building modules..."
./gradlew :modules:test-module:build :cli:build -x test

# Start test-module
echo ""
echo "4. Starting test-module on port 49095..."
cd modules/test-module
nohup ./gradlew quarkusDev -Dquarkus.http.port=39095 -Dquarkus.grpc.server.port=49095 > test-module.log 2>&1 &
TEST_PID=$!
cd ../..

echo "   Waiting for test-module to start (20 seconds)..."
sleep 20

# Check if module is running
echo ""
echo "5. Checking if test-module is running..."
if ! lsof -i:49095 > /dev/null 2>&1; then
    echo "✗ Test-module is not running on port 49095"
    echo "Check modules/test-module/test-module.log for errors"
    kill $TEST_PID 2>/dev/null
    exit 1
fi
echo "✓ Test-module is running"

# Test with grpcurl if available
if command -v grpcurl &> /dev/null; then
    echo ""
    echo "6. Testing module with grpcurl..."
    echo "   Available services:"
    grpcurl -plaintext localhost:49095 list
    
    echo ""
    echo "   Getting service registration info:"
    grpcurl -plaintext -d '{}' localhost:49095 com.rokkon.search.model.PipeStepProcessor/GetServiceRegistration
fi

# Run the CLI registration
echo ""
echo "7. Running CLI registration..."
echo "   Command: register --module-host localhost --module-port 49095 --engine-host localhost --engine-port 49094 --verbose"
echo ""

java -jar cli/build/quarkus-app/quarkus-run.jar register \
    --module-host localhost \
    --module-port 49095 \
    --engine-host localhost \
    --engine-port 49094 \
    --verbose

REGISTRATION_RESULT=$?

# Check Consul
echo ""
echo "8. Checking Consul for registered services..."
curl -s http://localhost:8500/v1/catalog/services | jq '.'

echo ""
echo "9. Checking specific module registration..."
curl -s http://localhost:8500/v1/catalog/service/test-processor | jq '.'

# Cleanup
echo ""
echo "10. Stopping test-module..."
kill $TEST_PID 2>/dev/null

echo ""
echo "=== Test Complete ==="
if [ $REGISTRATION_RESULT -eq 0 ]; then
    echo "✓ Registration successful!"
else
    echo "✗ Registration failed with code: $REGISTRATION_RESULT"
fi

echo ""
echo "Check logs:"
echo "  - modules/test-module/test-module.log - Module output"
echo "  - Engine console output - Registration service logs"