#!/bin/bash

echo "Testing Engine-to-Engine Dynamic gRPC Routing"
echo "============================================="

# First, let's check what gRPC services the engine implements
echo "1. Checking engine's gRPC services..."
curl -s http://localhost:38082/api/v1/modules/dashboard | jq '.base_services[] | select(.name == "pipeline-engine") | .instances[0].grpc_services'

# The engine implements PipeStreamEngine service, not PipeStepProcessor
# So we need to test with the correct service interface

echo -e "\n2. Checking if engine can discover itself via Consul..."
curl -s http://localhost:38082/api/v1/test/dynamic-grpc/services | jq .

# For engine-to-engine routing, we'd need to:
# 1. Register a second engine instance in Consul (simulated)
# 2. Have the first engine route to it using dynamic gRPC

echo -e "\n3. Simulating second engine registration in Consul..."
curl -X PUT http://localhost:8500/v1/agent/service/register -d '{
  "ID": "pipeline-engine-2",
  "Name": "pipeline-engine-downstream",
  "Tags": ["grpc", "pipeline-engine", "downstream"],
  "Address": "172.17.0.1",
  "Port": 48083,
  "Meta": {
    "grpc-port": "48083",
    "service-type": "ENGINE",
    "engine-id": "engine-2"
  },
  "Check": {
    "Name": "Engine gRPC Health",
    "GRPC": "172.17.0.1:48083",
    "Interval": "10s",
    "Timeout": "5s"
  }
}'

sleep 2

echo -e "\n\n4. Verifying downstream engine registration..."
curl -s http://localhost:8500/v1/health/service/pipeline-engine-downstream | jq '.[0].Service | {ID, Address, Port}'

echo -e "\n\n5. Engine should now be able to discover and route to 'pipeline-engine-downstream'"
echo "In a real pipeline, the engine would:"
echo "- Check if next step's processorInfo.grpcServiceName == 'pipeline-engine-downstream'"
echo "- Use DynamicGrpcClientFactory to get a PipeStreamEngine client"
echo "- Route the request using the engine's gRPC interface (not PipeStepProcessor)"

echo -e "\n\nNote: To fully test this, we'd need:"
echo "1. A second engine instance running on port 48083"
echo "2. Or modify the test endpoint to support PipeStreamEngine calls"