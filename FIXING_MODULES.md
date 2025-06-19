# Rokkon Engine Module Migration - Full TODO Summary

## Overview
We're migrating Rokkon Engine modules to use the new protobuf structure with `rokkon-protobuf` and `rokkon-commons` dependencies, implementing comprehensive tests that generate and process real test data through the pipeline.

## Completed Tasks ✅

### 1. Parser Module Migration
- **Updated dependencies**: Replaced `proto-definitions` with `rokkon-protobuf` and `rokkon-commons`
- **Configured Mutiny gRPC**: Set up reactive gRPC code generation
- **Created comprehensive test**: Processes 126 source documents through parser
- **Generated test data**: Successfully parsed 114 documents (12 failed due to EMF parser issues)
- **Saved outputs**: Parser input/output saved to `build/test-data/parser/`
- **Merged test utilities**: Moved RokkonTestProtosLoader methods into ProtobufTestDataHelper

### 2. Chunker Module Migration
- **Updated dependencies**: Migrated to new protobuf structure
- **Fixed UTF-8 issues**: Implemented UnicodeSanitizer to handle unpaired surrogates
- **Configured 1GB gRPC**: Both server and client support 1GB messages
- **Created double-chunk test**: Processes documents through chunker twice
- **Generated test data**: Successfully chunked 113 documents (1 OOM failure)
- **Saved outputs**: Chunker outputs saved to test-utilities as `chunker-pipe-docs`

### 3. Embedder Module Migration
- **Updated dependencies**: Migrated to new protobuf structure
- **Created dual-model test**: Tests with Nomic (768d) and BGE (384d) models
- **Configured test structure**: Set up comprehensive tests similar to parser/chunker

## Current Issues 🔧

### Embedder Test Data Loading Problem
The embedder tests are loading 0 chunked documents despite:
- Chunker data exists in test-utilities jar (113 files)
- ProtobufTestDataHelper.getChunkerPipeDocuments() returns empty collection
- Parser output documents load correctly (114 documents)
- Same pattern works in parser and chunker modules

**Next debugging steps:**
1. Check if there's a classloader/resource loading issue specific to chunker-pipe-docs
2. Verify the chunker files are valid protobuf messages
3. Test if manually reading one chunker file works
4. Compare with how parser output docs are loaded successfully

## Pending Tasks 📋

### High Priority
1. **Fix embedder test data loading** (BLOCKING)
   - Debug why getChunkerPipeDocuments() returns empty
   - Ensure embedder can process chunked documents
   - Save embedder outputs to test directories

2. **Update test-module**
   - Migrate to rokkon-protobuf and rokkon-commons
   - Update build.gradle.kts with Mutiny configuration
   - Create appropriate tests

3. **Update remaining modules**
   - Identify all modules that need gRPC stub compilation
   - Update their build.gradle files
   - Ensure consistent configuration across all modules

4. **Implement chunking strategies**
   - Token-based chunking
   - DJL token chunking
   - Semantic chunking
   - Character-based chunking
   - Enum-based configuration system

### Medium Priority
1. **Update chunker to use ProcessingBuffer from test-utilities**
   - Remove local ProcessingBuffer implementation
   - Use shared implementation from test-utilities

### Low Priority
1. **Fix EMF parser errors in Tika**
   - Investigate 12 documents that fail EMF parsing
   - Improve document compatibility

## Technical Debt & Notes

### Key Configuration Patterns
```yaml
# application.yml for gRPC modules
quarkus:
  grpc:
    server:
      max-inbound-message-size: 1073741824  # 1GB
      max-outbound-message-size: 1073741824
    clients:
      serviceName:
        max-inbound-message-size: 1073741824
```

### Build Configuration
```kotlin
// build.gradle.kts pattern
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}
```

### Test Data Flow
1. **Parser**: 126 source docs → 114 parsed docs
2. **Chunker**: 114 parsed docs → 113 chunked docs
3. **Embedder**: 113 chunked docs → ??? (blocked by loading issue)

## Module Migration Checklist

For each module that needs migration:

- [ ] Update `build.gradle.kts`:
  - [ ] Replace `proto-definitions` with `rokkon-protobuf`
  - [ ] Add `rokkon-commons` dependency
  - [ ] Configure Mutiny gRPC code generation
  - [ ] Remove manual proto extraction tasks

- [ ] Update `application.yml`:
  - [ ] Configure gRPC message sizes (1GB)
  - [ ] Use YAML format (not properties)
  - [ ] Set proper test profiles

- [ ] Update service implementation:
  - [ ] Ensure proper UTF-8 handling
  - [ ] Use reactive Mutiny patterns
  - [ ] Implement comprehensive error handling

- [ ] Create comprehensive tests:
  - [ ] Unit tests with `@QuarkusTest`
  - [ ] Integration tests with `@QuarkusIntegrationTest`
  - [ ] Test data generation/processing

## Key Learnings

### UTF-8 Handling
Java strings use UTF-16 internally, which can create unpaired surrogates. When converting to UTF-8 for protobuf:
- Use `UnicodeSanitizer.sanitizeInvalidUnicode()` for text fields
- Configure charset decoders with `CodingErrorAction.REPLACE`
- Test with large, complex documents

### Quarkus gRPC Configuration
- Quarkus automatically extracts protos from dependencies
- No manual `extractProtos` task needed
- Use `quarkus.grpc.codegen.type=mutiny` for reactive stubs
- Configure both server and client message sizes

### Test Data Management
- Use `ProtobufTestDataHelper` from test-utilities
- Save test outputs for downstream modules
- Use `ProcessingBuffer` for capturing test data
- Maintain consistent directory structure

## Next Immediate Tasks

1. **Debug embedder test data loading**
   ```java
   // Create minimal test to isolate the issue
   // Check protobuf message validity
   // Verify resource loading mechanism
   ```

2. **Complete embedder test execution**
   - Process documents through both models
   - Save outputs to respective directories
   - Verify embedding dimensions match expectations

3. **Move to test-module migration**
   - Apply same patterns as parser/chunker/embedder
   - Ensure consistent test structure

## Commands Reference

```bash
# Build and test module
./gradlew clean build

# Run specific test
./gradlew test --tests "*.comprehensive.TestName"

# Run integration tests
./gradlew quarkusIntTest

# Publish to Maven local
./gradlew publishToMavenLocal

# Clear gradle cache for specific dependency
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.rokkon.pipeline/[module-name]
```

## Status Summary

| Module | Dependencies Updated | Tests Created | Test Data Generated | Status |
|--------|---------------------|---------------|---------------------|---------|
| Parser | ✅ | ✅ | ✅ (114 docs) | Complete |
| Chunker | ✅ | ✅ | ✅ (113 docs) | Complete |
| Embedder | ✅ | ✅ | ❌ (blocked) | In Progress |
| Test Module | ❌ | ❌ | ❌ | Pending |
| Others | ❌ | ❌ | ❌ | Pending |