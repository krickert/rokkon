#!/bin/bash

# Script to test dynamic gRPC service discovery

echo "Testing Dynamic gRPC Service Discovery"
echo "======================================"

# Check if engine is running
echo "1. Checking engine health..."
curl -s http://localhost:38082/q/health | jq . || { echo "Engine not running!"; exit 1; }

# Register a test service in Consul
echo -e "\n2. Registering test-echo service in Consul..."
curl -X PUT http://localhost:8500/v1/agent/service/register -d '{
  "ID": "test-echo-1",
  "Name": "test-echo",
  "Tags": ["grpc", "pipeline-module"],
  "Address": "localhost",
  "Port": 49091,
  "Meta": {
    "grpc-port": "49091",
    "service-type": "MODULE"
  },
  "Check": {
    "TCP": "localhost:49091",
    "Interval": "10s"
  }
}'

# Wait for service to be registered
sleep 2

# Check if service is registered
echo -e "\n3. Verifying service registration..."
curl -s http://localhost:8500/v1/health/service/test-echo | jq '.[0].Service.ID'

# Create a pipeline that uses the service
echo -e "\n4. Creating pipeline with dynamic gRPC step..."
curl -X POST http://localhost:38082/api/v1/clusters/default/pipelines/test-dynamic-grpc \
  -H "Content-Type: application/json" \
  -d '{
    "pipelineName": "Dynamic gRPC Test",
    "description": "Tests dynamic service discovery",
    "steps": {
      "echo-step": {
        "stepName": "echo-step",
        "stepType": "PIPELINE",
        "description": "Echo via dynamic gRPC",
        "processorInfo": {
          "processorType": "ECHO",
          "grpcServiceName": "test-echo"
        },
        "transportConfig": {
          "transportType": "GRPC"
        },
        "outputRouting": {
          "routes": {}
        }
      }
    }
  }'

echo -e "\n\n5. Checking if pipeline was created..."
curl -s http://localhost:38082/api/v1/clusters/default/pipelines | jq '.[] | select(.pipelineId == "test-dynamic-grpc") | .pipelineName'

echo -e "\n\nDynamic gRPC test setup complete!"
echo "Next steps:"
echo "1. Start an echo module on port 49091"
echo "2. Execute the pipeline to test dynamic routing"