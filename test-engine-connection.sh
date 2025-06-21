#!/bin/bash

echo "Testing engine connection feature..."

# Register echo module with different engine connection
echo "Registering echo module with separate engine connection..."
curl -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-service-impl",
    "host": "172.17.0.3",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {},
    "engineConnection": {
      "host": "localhost",
      "port": 49090
    }
  }' | jq .

echo -e "\nChecking registered module details..."
curl -s http://localhost:8081/api/v1/global-modules | jq .

echo -e "\nChecking services in Consul..."
curl -s http://localhost:8081/api/v1/modules/all-services | jq .