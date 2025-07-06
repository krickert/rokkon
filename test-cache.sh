#!/bin/bash

# Test distributed cache invalidation

echo "Testing distributed cache invalidation system..."

# Create a test pipeline definition
echo "1. Creating test pipeline definition..."
curl -X POST http://localhost:39001/api/pipelines/definitions/test-cache-pipeline \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Cache Pipeline",
    "description": "Pipeline to test cache invalidation",
    "pipelineSteps": {
      "step1": {
        "name": "Echo Step",
        "description": "Test echo step",
        "serviceName": "echo",
        "outputType": "PASS_THROUGH",
        "configParams": {}
      }
    }
  }'

echo -e "\n\n2. Fetching pipeline (should hit Consul first time)..."
time curl -s http://localhost:39001/api/pipelines/definitions/test-cache-pipeline | jq '.name'

echo -e "\n\n3. Fetching pipeline again (should hit cache)..."
time curl -s http://localhost:39001/api/pipelines/definitions/test-cache-pipeline | jq '.name'

echo -e "\n\n4. Updating pipeline directly in Consul to trigger watch..."
# This simulates another engine instance updating the data
consul kv put pipeline/pipelines/definitions/test-cache-pipeline '{
  "name": "Test Cache Pipeline - UPDATED",
  "description": "Pipeline to test cache invalidation - UPDATED",
  "pipelineSteps": {
    "step1": {
      "name": "Echo Step",
      "description": "Test echo step",
      "serviceName": "echo",
      "outputType": "PASS_THROUGH",
      "configParams": {}
    }
  }
}'

echo -e "\n\n5. Waiting for watch to fire and invalidate cache..."
sleep 3

echo -e "\n\n6. Fetching pipeline again (should show updated data from Consul)..."
time curl -s http://localhost:39001/api/pipelines/definitions/test-cache-pipeline | jq '.name'

echo -e "\n\n7. Cleaning up test data..."
curl -X DELETE http://localhost:39001/api/pipelines/definitions/test-cache-pipeline

echo -e "\n\nTest complete!"