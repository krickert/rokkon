#!/bin/bash

echo "Testing Distributed Pipeline with Fixed Service Names"
echo "===================================================="

# First, create a new pipeline definition with the fixed code
echo "1. Creating new pipeline definition (will use module names, not instance IDs):"
cat > /tmp/distributed-pipeline-def.json << 'EOF'
{
  "name": "Distributed Echo-Test Pipeline",
  "description": "Pipeline that properly uses module names for distributed processing",
  "steps": [
    {
      "name": "echo-step",
      "module": "echo-4ae6f2cd",
      "config": {
        "prefix": "DISTRIBUTED: ",
        "suffix": " [echo processed]"
      },
      "outputs": {
        "default": {
          "transportType": "GRPC",
          "targetStepName": "test-step"
        }
      }
    },
    {
      "name": "test-step", 
      "module": "test-84d6e20a",
      "config": {
        "mode": "validate",
        "add_metadata": true
      }
    }
  ]
}
EOF

curl -X POST http://localhost:38082/api/v1/pipelines/definitions \
  -H "Content-Type: application/json" \
  -d @/tmp/distributed-pipeline-def.json | jq '.'

echo -e "\n2. Deploy the new pipeline to cluster:"
# Get the pipeline definition we just created
PIPELINE_CONFIG=$(curl -s "http://localhost:38082/api/v1/pipelines/definitions/Distributed+Echo-Test+Pipeline" | jq -c '.')

if [ "$PIPELINE_CONFIG" == "null" ] || [ -z "$PIPELINE_CONFIG" ]; then
    echo "Error: Could not retrieve pipeline definition"
    exit 1
fi

curl -X POST http://localhost:38082/api/v1/clusters/default-cluster/pipelines/distributed-echo-test \
  -H "Content-Type: application/json" \
  -d "$PIPELINE_CONFIG" | jq '.'

echo -e "\n3. Verify the deployed pipeline uses module names (not instance IDs):"
curl -s http://localhost:38082/api/v1/clusters/default-cluster/pipelines/distributed-echo-test | \
  jq '.pipelineSteps | to_entries[] | {step: .key, grpcServiceName: .value.processorInfo.grpcServiceName}'

echo -e "\n4. Test the distributed pipeline:"
cat > /tmp/test-distributed.json << 'EOF'
{
  "stream_id": "distributed-test-001",
  "current_pipeline_name": "distributed-echo-test",
  "action_type": "CREATE",
  "current_hop_number": 0,
  "document": {
    "id": "dist-doc-001",
    "title": "Distributed Processing Test",
    "body": "This document will be processed by any available echo instance, then any available test instance",
    "document_type": "test",
    "source_uri": "test://distributed",
    "metadata": {
      "test_type": "distributed_load_balancing"
    }
  }
}
EOF

echo -e "\n5. Send to PipeStreamEngine:"
grpcurl -plaintext -d "$(cat /tmp/test-distributed.json)" \
  localhost:48082 com.rokkon.search.engine.PipeStreamEngine/processPipeAsync

echo -e "\n✅ If successful, the pipeline will:"
echo "   - Use 'echo' service name (not echo-4ae6f2cd)"
echo "   - Use 'test' service name (not test-84d6e20a)"
echo "   - Enable load balancing across multiple instances"
echo "   - Support true distributed processing!"