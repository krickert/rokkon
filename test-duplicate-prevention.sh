#!/bin/bash

echo "Testing duplicate registration prevention..."

# First, ensure the echo module is registered
echo "Current global modules:"
curl -s http://localhost:8081/api/v1/global-modules | jq .

echo -e "\n\nAttempting to register echo module again (should fail with 409)..."
response=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-impl",
    "host": "localhost",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {}
  }')

# Extract the response body and status code
body=$(echo "$response" | head -n -1)
status_code=$(echo "$response" | tail -n 1)

echo "Status Code: $status_code"
echo "Response Body:"
echo "$body" | jq .

if [ "$status_code" == "409" ]; then
    echo -e "\n✅ Success! Duplicate registration was correctly prevented with HTTP 409 Conflict"
else
    echo -e "\n❌ Error! Expected HTTP 409 but got $status_code"
fi