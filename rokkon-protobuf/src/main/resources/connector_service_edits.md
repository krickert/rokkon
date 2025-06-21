oh # Edits needed for connector_service.proto

## 1. Add BatchInfo message (insert after ConnectorRequest, before ConnectorResponse)

```proto
// Information about a batch operation
message BatchInfo {
  // Unique identifier for this batch
  string batch_id = 1;
  
  // Total number of items in the batch
  int64 total_items = 2;
  
  // This item's position in the batch (1-based)
  int64 current_item_number = 3;
  
  // Human-readable name/description of the batch
  string batch_name = 4;
  
  // When this batch started processing
  google.protobuf.Timestamp started_at = 5;
  
  // Source of the batch (e.g., "pg_catalog.csv", "wikipedia-20240101.json")
  optional string source_reference = 6;
}
```

## 2. Update ConnectorResponse (replace the existing ConnectorResponse)

```proto
message ConnectorResponse {
  // The unique stream_id assigned by the engine for tracking this document
  string stream_id = 1;
  
  // Whether the document was accepted for processing
  bool accepted = 2;
  
  // Human-readable message (success or error details)
  string message = 3;
  
  // If not accepted, specific error information
  optional ErrorInfo error = 4;
  
  // Server timestamp when the request was received
  google.protobuf.Timestamp received_at = 5;
  
  // Estimated queue position (if queuing is implemented)
  optional int64 queue_position = 6;
}
```

## 3. Add ErrorInfo message (after ConnectorResponse)

```proto
// Error information for rejected documents
message ErrorInfo {
  // Machine-readable error code
  enum ErrorCode {
    UNKNOWN_ERROR = 0;
    INVALID_CONNECTOR_TYPE = 1;
    DOCUMENT_VALIDATION_FAILED = 2;
    QUOTA_EXCEEDED = 3;
    DUPLICATE_DOCUMENT = 4;
    PIPELINE_NOT_CONFIGURED = 5;
    MISSING_REQUIRED_FIELD = 6;
  }
  
  ErrorCode code = 1;
  
  // Human-readable error message
  string message = 2;
  
  // Specific validation errors (field-level)
  repeated string field_errors = 3;
}
```

## 4. Add batch processing messages (after ErrorInfo)

```proto
// Batch request for processing multiple documents at once
message BatchConnectorRequest {
  // List of documents to process
  repeated ConnectorRequest requests = 1;
  
  // Common batch information (overrides individual batch_info if present)
  optional BatchInfo common_batch_info = 2;
  
  // Whether to process all or none (default: false, process what we can)
  optional bool atomic = 3;
}

// Response for batch processing
message BatchConnectorResponse {
  // Individual responses for each request (same order as requests)
  repeated ConnectorResponse responses = 1;
  
  // Summary statistics
  int32 total_submitted = 2;
  int32 accepted_count = 3;
  int32 rejected_count = 4;
  
  // Overall batch status
  bool all_accepted = 5;
  
  // Batch-level errors (if atomic=true and batch failed)
  optional string batch_error = 6;
}
```

## 5. Update the ConnectorEngine service (replace existing service)

```proto
// Main service interface implemented by the Rokkon Engine (Java)
// Connectors in any language call this service to submit documents
service ConnectorEngine {
  // Process a single document
  rpc ProcessDocument(ConnectorRequest) returns (ConnectorResponse);
  
  // Process multiple documents in one call (more efficient for batches)  
  rpc ProcessBatch(BatchConnectorRequest) returns (BatchConnectorResponse);
  
  // Stream documents for large batches or real-time feeds
  rpc ProcessStream(stream ConnectorRequest) returns (stream ConnectorResponse);
}
```

## Summary of changes:
1. Changed `source_identifier` to `connector_type`
2. Added `tags` field for categorization
3. Added `BatchInfo` for batch tracking
4. Changed `initial_context_params` to `context_params`
5. Added `priority` and `connector_version` fields
6. Enhanced `ConnectorResponse` with error handling
7. Added batch and streaming RPCs to the service