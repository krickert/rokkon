#!/bin/bash

echo "Running Playwright UI Tests"
echo "=========================="
echo ""
echo "Prerequisites:"
echo "1. Ensure dev mode is running: ./gradlew :engine:pipestream:quarkusDev"
echo "2. Playwright browsers installed: npx playwright install"
echo ""

# Check if dev mode is running
if ! curl -s -f http://localhost:39001/api/v1/engine/info > /dev/null; then
    echo "ERROR: Dev mode is not running at http://localhost:39001"
    echo "Please start it with: ./gradlew :engine:pipestream:quarkusDev"
    exit 1
fi

echo "✓ Dev mode is running"
echo ""

# Run the tests with configuration cache disabled
echo "Running tests..."
./gradlew :engine:pipestream:test \
    --tests "DashboardLiveTest" \
    -Dtest.ui.devmode=true \
    --no-configuration-cache \
    --console=plain

echo ""
echo "Test run complete!"
echo "Check the test report at: engine/pipestream/build/reports/tests/test/index.html"