#!/bin/bash

# Test the Docker container with grpcurl

echo "Testing gRPC server health..."
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check

echo -e "\n\nTesting service registration..."
grpcurl -plaintext localhost:9090 com.rokkon.search.model.PipeStepProcessor/GetServiceRegistration

echo -e "\n\nTesting processData with valid document..."
grpcurl -plaintext -d '{
  "document": {
    "id": "test-123",
    "title": "Test Document",
    "body": "This is a test document for Docker testing"
  },
  "metadata": {
    "pipeline_name": "test-pipeline",
    "pipe_step_name": "test-processor",
    "stream_id": "test-stream-1",
    "current_hop_number": 1
  }
}' localhost:9090 com.rokkon.search.model.PipeStepProcessor/ProcessData

echo -e "\n\nTesting schema validation mode..."
grpcurl -plaintext -d '{
  "document": {
    "id": "test-456",
    "body": "Missing title - should fail validation"
  },
  "metadata": {
    "pipeline_name": "test-pipeline",
    "pipe_step_name": "test-processor",
    "stream_id": "test-stream-1",
    "current_hop_number": 1
  },
  "config": {
    "custom_json_config": {
      "mode": "validate"
    }
  }
}' localhost:9090 com.rokkon.search.model.PipeStepProcessor/ProcessData

echo -e "\n\nDocker container test complete!"
