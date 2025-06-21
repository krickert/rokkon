#!/bin/bash

echo "Starting Rokkon Engine in development mode..."
echo "============================================"
echo "HTTP Port: 38090"
echo "gRPC Port: 49000"
echo "Debug Port: 5005"
echo ""

# Check if required ports are available
if lsof -i :38090 >/dev/null 2>&1; then
    echo "❌ ERROR: Port 38090 is already in use!"
    exit 1
fi

if lsof -i :49000 >/dev/null 2>&1; then
    echo "❌ ERROR: Port 49000 is already in use!"
    exit 1
fi

if lsof -i :5005 >/dev/null 2>&1; then
    echo "❌ ERROR: Debug port 5005 is already in use!"
    exit 1
fi

# Run the engine
./gradlew :rokkon-engine:quarkusDev