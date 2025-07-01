#!/bin/bash

echo "Deploying Echo Test Pipeline to default-cluster"
echo "=============================================="

# First, let's check what pipeline definition we have
echo "1. Checking pipeline definition:"
curl -s http://localhost:38082/api/v1/pipelines/definitions | jq '.'

# Get the full pipeline definition to deploy
echo -e "\n2. Getting full pipeline definition:"
PIPELINE_DEF=$(curl -s "http://localhost:38082/api/v1/pipelines/definitions/Echo+Test+Fixed" | jq -c '.')

if [ "$PIPELINE_DEF" == "null" ] || [ -z "$PIPELINE_DEF" ]; then
    echo "Error: Could not retrieve pipeline definition"
    exit 1
fi

echo "Pipeline definition retrieved"

# Deploy to cluster
echo -e "\n3. Deploying pipeline to default-cluster:"
curl -X POST http://localhost:38082/api/v1/clusters/default-cluster/pipelines/echo-test-grpc-v2 \
  -H "Content-Type: application/json" \
  -d "$PIPELINE_DEF" | jq '.'

# Verify deployment
echo -e "\n4. Verifying pipeline deployment:"
curl -s http://localhost:38082/api/v1/clusters/default-cluster/pipelines | jq '.'

echo -e "\n5. Getting specific pipeline config:"
curl -s http://localhost:38082/api/v1/clusters/default-cluster/pipelines/echo-test-grpc-v2 | jq '.'