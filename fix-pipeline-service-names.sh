#!/bin/bash

echo "Fixing Pipeline Service Names"
echo "============================"

# Create corrected pipeline with simple service names
cat > /tmp/echo-test-pipeline-fixed.json << 'EOF'
{
  "name": "Echo Test Pipeline",
  "pipelineSteps": {
    "entry": {
      "stepName": "entry",
      "stepType": "INITIAL_PIPELINE",
      "description": "Entry point",
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
      "customConfig": {
        "jsonConfig": {
          "prefix": "ECHO: ",
          "suffix": " [processed]"
        }
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
      },
      "customConfig": {
        "jsonConfig": {
          "mode": "validate",
          "add_metadata": true
        }
      }
    }
  }
}
EOF

echo "1. Deploying corrected pipeline:"
curl -X PUT http://localhost:38082/api/v1/clusters/default-cluster/pipelines/echo-test-grpc-v2 \
  -H "Content-Type: application/json" \
  -d @/tmp/echo-test-pipeline-fixed.json | jq '.'

echo -e "\n2. Verifying updated pipeline:"
curl -s http://localhost:38082/api/v1/clusters/default-cluster/pipelines/echo-test-grpc-v2 | jq '.pipelineSteps[].processorInfo'

echo -e "\n3. Now testing with corrected pipeline:"
cat > /tmp/test-corrected-pipeline.json << 'EOF'
{
  "stream_id": "corrected-test-001",
  "current_pipeline_name": "echo-test-grpc-v2",
  "action_type": "CREATE",
  "current_hop_number": 0,
  "document": {
    "id": "corrected-doc-001",
    "title": "Test with Corrected Service Names",
    "body": "This should now work with echo and test services",
    "document_type": "test",
    "source_uri": "test://corrected",
    "metadata": {
      "test": "corrected-service-names"
    }
  }
}
EOF

echo -e "\n4. Sending to engine:"
grpcurl -plaintext -d "$(cat /tmp/test-corrected-pipeline.json)" \
  localhost:48082 com.rokkon.search.engine.PipeStreamEngine/processPipeAsync