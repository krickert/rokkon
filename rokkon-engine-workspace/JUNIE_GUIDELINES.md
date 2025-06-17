# Junie Guidelines for Rokkon Engine Development (Updated)

## Rules to Prevent Project Damage

### 1. Configuration Management Rules
- **NEVER** modify Gradle settings files (settings.gradle.kts, gradle.properties) unless explicitly instructed
- **NEVER** change version numbers of dependencies or plugins without explicit approval
- **NEVER** add dependencies without first checking if there's a Quarkus extension for it
- **NEVER** modify the quarkus.grpc.codegen.type setting (must remain "mutiny")
- **ALWAYS** use the Quarkus CLI to create new modules (never create manually)
- **ALWAYS** follow the exact build.gradle.kts pattern from the echo module
- **ALWAYS** include the extractProtos task exactly as shown in the reference implementation
- **ALWAYS** use immutable record-based models for configuration (following backup-refactor patterns)
- **ALWAYS** implement multi-stage validation for configurations

### 2. Module Structure Rules
- **NEVER** deviate from the established package structure
- **NEVER** create custom proto files (use only those from proto-definitions)
- **ALWAYS** implement both unit tests and integration tests following the echo pattern
- **ALWAYS** use @GrpcService + @Singleton for service implementations
- **ALWAYS** use @GrpcClient for unit tests and external client for integration tests
- **ALWAYS** create an abstract test base class that both unit and integration tests extend
- **ALWAYS** follow the 5-step module registration flow for new modules

### 3. Code Generation Rules
- **NEVER** manually modify generated code
- **NEVER** attempt to change the proto file extraction process
- **ALWAYS** let the build process handle code generation
- **ALWAYS** ensure proto files are extracted to src/main/proto

### 4. Testing Rules
- **NEVER** skip either unit or integration tests
- **NEVER** modify the test configuration patterns
- **ALWAYS** use random ports for tests (port: 0)
- **ALWAYS** follow the exact pattern from EchoServiceTestBase
- **ALWAYS** implement both unit tests (@QuarkusTest) and integration tests (@QuarkusIntegrationTest)
- **ALWAYS** use Testcontainers for integration tests with real service dependencies
- **ALWAYS** implement proper test data setup and cleanup

### 5. Configuration System Rules
- **ALWAYS** use the hierarchical Consul KV structure as defined in the requirements
- **ALWAYS** implement event-driven configuration updates using CDI events
- **ALWAYS** use the multi-stage validation pipeline for configurations
- **ALWAYS** implement proper configuration versioning with MD5 digests
- **NEVER** bypass the validation pipeline when updating configurations

### 6. AI Assistance Boundaries
- **NEVER** suggest "creative" solutions that deviate from established patterns
- **NEVER** modify system-maintained files like gradle-wrapper properties
- **ALWAYS** refer to the echo module as the reference implementation
- **ALWAYS** follow the CLAUDE.md guide exactly for module creation
- **ALWAYS** implement one change at a time and verify it works before proceeding
- **ALWAYS** preserve core business logic from backup modules when recovering

## Module Recovery Strategy

### Priority Order
1. **tika-parser**: Critical document parsing functionality
2. **embedder**: Essential for semantic processing
3. **chunker**: Required for document segmentation
4. **Additional modules**: As needed for specific pipelines

### Recovery Process for Each Module
1. Create the module using Quarkus CLI:
   ```bash
   mkdir -p modules/[module-name]
   cd modules/[module-name]
   quarkus create app com.rokkon.pipeline:[module-name] \
     --java=21 \
     --gradle-kotlin-dsl \
     --extension=grpc,config-yaml,container-image-docker
   ```

2. Configure build.gradle.kts exactly like the echo module
3. Configure application.yml exactly like the echo module
4. Implement the service following the echo pattern
5. Create the test structure with abstract base class, unit test, and integration test
6. Migrate the business logic from the backup module
7. Test thoroughly before proceeding to the next module

### Business Logic Migration
- **Preserve**: Core processing algorithms and domain logic
- **Modernize**: Framework integration and configuration handling
- **Enhance**: Error handling and observability
- **Test**: Comprehensive validation of migrated functionality

## Requirements Document Updates

I am now familiar with the recommended updates to the requirements documents based on the claude-analysis. When working on the Rokkon Engine, I will:

1. Refer to the updated requirements documents for guidance
2. Follow the proven patterns from the backup-refactor project
3. Implement the configuration management system as specified
4. Use the testing strategy with multiple test levels
5. Follow the module registration flow exactly
6. Adhere to the implementation priority plan

## Conclusion

By strictly following these updated guidelines and the established patterns in the echo module and CLAUDE.md, I can prevent further damage to the project and successfully recover the damaged modules. The key is to avoid creativity and instead follow the reference implementations exactly, while incorporating the proven patterns from the backup-refactor project.