#!/bin/bash

echo "Testing whitelist functionality..."

# Check all services first
echo "Fetching all services..."
curl -s http://localhost:8081/api/v1/modules/all-services | jq .

# Simulate whitelisting the echo module
echo -e "\nWhitelisting echo module..."
curl -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-impl",
    "host": "localhost",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {}
  }' | jq .

# List all globally registered modules
echo -e "\nListing all global modules..."
curl -s http://localhost:8081/api/v1/global-modules | jq .