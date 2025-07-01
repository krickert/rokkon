#!/bin/bash

echo "Testing Pipeline Execution via ConnectorEngine"
echo "============================================="

# List available gRPC services
echo "1. Available gRPC services on engine:"
grpcurl -plaintext localhost:48082 list | grep -E "(Connector|PipeStream)"

# Create a ConnectorRequest
cat > /tmp/connector-request.json << 'EOF'
{
  "connector_type": "test-connector",
  "suggested_stream_id": "test-stream-001",
  "document": {
    "id": "engine-test-001",
    "title": "Test Document for Engine Pipeline",
    "body": "This document should flow through echo -> test module via engine routing",
    "document_type": "test",
    "source_uri": "test://engine-pipeline",
    "metadata": {
      "source": "engine-test",
      "timestamp": "2025-07-01T03:00:00Z"
    }
  }
}
EOF

echo -e "\n2. Sending ConnectorRequest to ConnectorEngine..."
echo "Request:"
cat /tmp/connector-request.json | jq .

echo -e "\n3. Response from engine:"
grpcurl -plaintext -d "$(cat /tmp/connector-request.json)" \
  localhost:48082 com.rokkon.search.engine.ConnectorEngine/processConnectorDoc 2>&1 | tee /tmp/connector-response.txt

echo -e "\n4. Let's also test with PipeStreamEngine using a PipeStream message:"
cat > /tmp/pipestream-request.json << 'EOF'
{
  "stream_id": "test-stream-002",
  "current_pipeline_name": "Echo Test Fixed",
  "action_type": "CREATE",
  "document": {
    "id": "engine-test-002",
    "title": "Test Document for PipeStream",
    "body": "Testing PipeStream routing through echo -> test pipeline",
    "document_type": "test",
    "source_uri": "test://pipestream",
    "metadata": {
      "source": "pipestream-test",
      "timestamp": "2025-07-01T03:00:00Z"
    }
  }
}
EOF

echo -e "\n5. Sending PipeStream to PipeStreamEngine..."
echo "Request:"
cat /tmp/pipestream-request.json | jq .

echo -e "\n6. Response from PipeStreamEngine:"
grpcurl -plaintext -d "$(cat /tmp/pipestream-request.json)" \
  localhost:48082 com.rokkon.search.engine.PipeStreamEngine/processPipeAsync 2>&1