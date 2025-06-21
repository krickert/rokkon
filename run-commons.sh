#!/bin/bash

echo "Starting Rokkon Commons in development mode..."
echo "==========================================="
echo "HTTP Port: 38096"
echo "Debug Port: 5015"
echo ""

# Check if required ports are available
if lsof -i :38096 >/dev/null 2>&1; then
    echo "❌ ERROR: Port 38096 is already in use!"
    exit 1
fi

if lsof -i :5015 >/dev/null 2>&1; then
    echo "❌ ERROR: Debug port 5015 is already in use!"
    exit 1
fi

# Run commons with its own debug port
./gradlew :rokkon-commons:quarkusDev -Ddebug=5015