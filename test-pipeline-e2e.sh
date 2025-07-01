#!/bin/bash

echo "Testing End-to-End Pipeline Execution"
echo "====================================="

# Test with PipeStreamEngine using the deployed pipeline name
echo "1. Creating PipeStream request for deployed pipeline:"
cat > /tmp/pipestream-e2e.json << 'EOF'
{
  "stream_id": "e2e-test-001",
  "current_pipeline_name": "echo-test-grpc-v2",
  "action_type": "CREATE",
  "current_hop_number": 0,
  "document": {
    "id": "e2e-doc-001",
    "title": "End-to-End Test Document",
    "body": "This document should flow through echo -> test module via engine routing",
    "document_type": "test",
    "source_uri": "test://e2e-pipeline",
    "metadata": {
      "source": "e2e-test",
      "timestamp": "2025-07-01T03:30:00Z"
    }
  }
}
EOF

echo "Request:"
cat /tmp/pipestream-e2e.json | jq .

echo -e "\n2. Sending to PipeStreamEngine on gRPC port 48082:"
grpcurl -plaintext -d "$(cat /tmp/pipestream-e2e.json)" \
  localhost:48082 com.rokkon.search.engine.PipeStreamEngine/processPipeAsync

echo -e "\n3. Let's also check the engine logs to see the routing:"
echo "Run: docker logs rokkon-engine-1 | grep -E '(echo|test|routing|executing)' | tail -20"

echo -e "\n4. Alternative: Create a default-pipeline mapping for ConnectorEngine:"
cat > /tmp/default-pipeline-config.json << 'EOF'
{
  "name": "Default Pipeline",
  "pipelineSteps": {
    "entry": {
      "stepName": "entry",
      "stepType": "INITIAL_PIPELINE",
      "description": "Entry point for default pipeline",
      "outputs": {
        "default": {
          "transportType": "GRPC",
          "targetStepName": "echo-step",
          "grpcTransport": {
            "serviceName": "echo"
          }
        }
      }
    },
    "echo-step": {
      "stepName": "echo-step",
      "stepType": "PIPELINE",
      "description": "Echo processor",
      "processorInfo": {
        "grpcServiceName": "echo"
      },
      "outputs": {
        "default": {
          "transportType": "GRPC",
          "targetStepName": "test-step",
          "grpcTransport": {
            "serviceName": "test"
          }
        }
      }
    },
    "test-step": {
      "stepName": "test-step",
      "stepType": "SINK",
      "description": "Test processor (final step)",
      "processorInfo": {
        "grpcServiceName": "test"
      }
    }
  }
}
EOF

echo -e "\n5. Deploy default-pipeline (for ConnectorEngine):"
curl -X POST http://localhost:38082/api/v1/clusters/default-cluster/pipelines/default-pipeline \
  -H "Content-Type: application/json" \
  -d @/tmp/default-pipeline-config.json | jq '.'"