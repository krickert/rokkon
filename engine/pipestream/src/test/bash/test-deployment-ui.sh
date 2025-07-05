#!/bin/bash

echo "Testing Module Deployment UI with SSE"
echo "======================================"
echo ""
echo "Prerequisites:"
echo "1. Run: ./gradlew :engine:pipestream:quarkusDev"
echo "2. Wait for the application to start"
echo "3. Run this script in another terminal"
echo ""
echo "This script will:"
echo "- Connect to SSE endpoint for deployment events"
echo "- Trigger a module deployment"
echo "- Show real-time updates"
echo ""

# Check if dev mode is running
if ! curl -s -f http://localhost:39001/api/v1/engine/info > /dev/null; then
    echo "ERROR: Dev mode is not running!"
    echo "Please start it with: ./gradlew :engine:pipestream:quarkusDev"
    exit 1
fi

echo "✓ Dev mode is running"
echo ""

# Start SSE listener in background
echo "Starting SSE listener for deployment events..."
(
    curl -N -H "Accept: text/event-stream" \
        http://localhost:39001/api/v1/module-deployment/events 2>/dev/null | \
    while IFS= read -r line; do
        if [[ $line == data:* ]]; then
            echo "[SSE Event] ${line#data:}"
        fi
    done
) &
SSE_PID=$!

# Give SSE time to connect
sleep 2

echo ""
echo "Deploying echo module..."
echo ""

# Deploy echo module
response=$(curl -s -X POST http://localhost:39001/api/v1/module-management/echo/deploy)
echo "Deploy response: $response"

# Wait for deployment events
echo ""
echo "Waiting for deployment events (30 seconds)..."
sleep 30

# Clean up
kill $SSE_PID 2>/dev/null

echo ""
echo "Test complete!"
echo ""
echo "To see the UI:"
echo "1. Open http://localhost:39001/ in your browser"
echo "2. Click the 'Deploy Module' button"
echo "3. Select 'echo' from the dropdown"
echo "4. Watch the rocket animation during deployment!"