# Rokkon Test Inventory

This document tracks all tests in the Rokkon project and their migration status to the base/unit/integration pattern.

## Summary Statistics
- Total Test Files: 172
- Unit Tests: 108
- Integration Tests: 64
- Tests Already Migrated: 4 (EngineLifecycle, CustomClusterLifecycle, MethodicalBuildUp - partial)

## Legend
- ✅ Migrated to base/unit/integration pattern
- 🔧 In progress
- ❌ Not yet migrated
- 🚫 Disabled/needs fixing
- ⚠️ May need new test profile

## Test Inventory by Module

### engine/consul (22 unit tests, 9 integration tests)
#### Unit Tests (src/test/java)
- ✅ BasicConsulConnectionTest.java → BasicConsulConnectionUnitTest + IT (PASSING)
- ✅ ConsulConfigFailOnMissingTest.java (PASSING)
- ✅ ConsulConfigLoadingTest.java → moved to IT (requires real Consul)
- ✅ ConsulConfigSuccessFailTest.java → ConsulConfigSuccessFailUnitTest + IT (PASSING)
- ✅ IsolatedConsulKvTest.java → IsolatedConsulKvUnitTest + IT (PASSING)
- ✅ MethodicalBuildUpTest.java → base + unit + IT (PASSING)
- ✅ ParallelConsulKvTest.java → ParallelConsulKvUnitTest (PASSING - converted to non-Quarkus test)
- ⚠️ service/ClusterServiceTest.java → ClusterServiceUnitTest + IT (SKIPPED when run with all tests, passes individually)
- ⚠️ service/ModuleWhitelistServiceSimpleTest.java → ModuleWhitelistServiceSimpleUnitTest + IT (SKIPPED when run with all tests, passes individually)
- ⚠️ service/ModuleWhitelistServiceTest.java → base + unit + IT (SKIPPED when run with all tests, passes individually)
- ⚠️ service/PipelineConfigServiceTest.java → already has base/unit/IT pattern (SKIPPED when run with all tests, passes individually)
- 🚫 config/ConsulConfigSourceSimpleTest.java (FAILING - shutdown error when run with other tests, passes individually)

#### Integration Tests (src/integrationTest/java)
- ❌ api/ClusterResourceIT.java
- ❌ api/PipelineConfigResourceIT.java
- ❌ ConsulConfigIsolatedIT.java
- ✅ MethodicalBuildUpIT.java (already proper IT)
- ❌ service/ModuleWhitelistServiceContainerIT.java
- ❌ service/ModuleWhitelistServiceIT.java
- ❌ service/PipelineConfigServiceIT.java

### rokkon-engine (8 unit tests, 46 integration tests)
#### Unit Tests (src/test/java)
- ❌ api/GlobalModuleResourceTest.java
- ❌ api/PipelineDefinitionResourceTest.java
- ❌ api/PipelineInstanceResourceTest.java
- ❌ api/SimpleClusterResourceTest.java
- ❌ grpc/ModuleRegistrationServiceImplTest.java
- ❌ validation/ValidatorComponentTest.java (4 tests pass - uses RealValidatorsTestProfile)
- ✅ engine/CustomClusterLifecycleUnitTest.java
- ✅ engine/EngineLifecycleUnitTest.java

#### Integration Tests (src/integrationTest/java)
- Multiple IT files for various components (46 total)
- ✅ engine/EngineLifecycleIT.java
- ✅ engine/CustomClusterLifecycleIT.java
- Others need review after unit tests are fixed

### engine/validators (13 unit tests, 11 integration tests)
#### Unit Tests (src/test/java)
- ❌ validators/InterPipelineLoopValidatorTest.java
- ❌ validators/IntraPipelineLoopValidatorTest.java
- ❌ validators/KafkaTopicNamingValidatorTest.java
- ❌ validators/NamingConventionValidatorTest.java
- ❌ validators/OutputRoutingValidatorTest.java
- ❌ validators/ProcessorInfoValidatorTest.java
- ❌ validators/RequiredFieldsValidatorTest.java
- ❌ validators/RetryConfigValidatorTest.java
- ❌ validators/StepReferenceValidatorTest.java
- ❌ validators/StepTypeValidatorTest.java
- ❌ validators/TransportConfigValidatorTest.java
- ❌ validators/TransportConfigValidatorExtendedTest.java

#### Integration Tests (src/integrationTest/java)
- 11 corresponding IT files for validators

### engine/models (13 unit tests, 10 integration tests)
#### Unit Tests (src/test/java)
- ❌ AbstractJsonSerdeTest.java (base class)
- ❌ GrpcTransportConfigTest.java
- ❌ KafkaInputDefinitionTest.java
- ❌ KafkaTransportConfigTest.java
- ❌ PipelineClusterConfigTest.java
- ❌ PipelineConfigAdvancedTest.java
- ❌ PipelineConfigTest.java
- ❌ PipelineGraphConfigTest.java
- ❌ PipelineModuleConfigurationTest.java
- ❌ PipelineModuleMapTest.java
- ❌ PipelineStepConfigTest.java
- ❌ SchemaReferenceTest.java
- ❌ StepTypeTest.java
- ❌ TransportTypeTest.java

### modules/* (31 unit tests across all module subdirectories)
#### modules/chunker (3 tests)
- ❌ ChunkerServiceTest.java
- ❌ comprehensive/DoubleChunkProcessingTest.java
- ❌ comprehensive/SimpleChunkGenerationTest.java

#### modules/connectors/filesystem-crawler (5 unit, 11 integration)
- ❌ FilesystemCrawlerConnectorTest.java
- ❌ FilesystemCrawlerHealthCheckTest.java
- ❌ FilesystemCrawlerIntegrationTest.java
- ❌ FilesystemCrawlerResourceTest.java
- ❌ SwaggerUIIntegrationTest.java
- ❌ mock/MockConnectorEngineTest.java
- ❌ mock/MockConnectorEngineUnitTest.java

#### modules/echo (1 test)
- ❌ EchoServiceTest.java

#### modules/embedder (2 tests)
- ❌ EmbedderServiceTest.java
- ❌ comprehensive/EmbedderComprehensiveTest.java

#### modules/parser (5 tests)
- ❌ ParserServiceTest.java
- ❌ TikaTestDataGenerationTest.java
- ❌ comprehensive/ParserServiceComprehensiveTest.java
- ❌ comprehensive/ParserServiceRegistrationTest.java
- ❌ comprehensive/SourceDocumentProcessingTest.java

#### modules/proxy-module (1 test)
- ❌ PipeStepProcessorProxyTest.java

#### modules/test-module (10 unit, 5 integration)
- ❌ health/GrpcHealthCheckTest.java
- ❌ health/MixedHealthCheckTest.java
- ❌ health/QuarkusContainerHealthCheckTest.java
- ❌ health/SimpleHealthCheckTest.java
- ❌ health/StandaloneGrpcHealthCheckDockerTest.java
- ❌ TestHarnessServiceTest.java
- ❌ TestProcessorHelperTest.java
- ❌ TestProcessorUnitTest.java

### rokkon-commons (12 tests)
- ❌ events/ConsulConnectionEventTest.java
- ❌ events/ModuleRegistrationRequestEventTest.java
- ❌ events/ModuleRegistrationResponseEventTest.java
- ❌ jackson/JsonOrderingCustomizerTest.java
- ❌ utils/NoOpProcessingBufferTest.java
- ❌ utils/ObjectMapperFactoryTest.java
- ❌ utils/ProcessingBufferFactoryTest.java
- ❌ utils/ProcessingBufferImplTest.java
- ❌ utils/ProcessingBufferTest.java
- ❌ utils/ProtoFieldMapperTest.java

### test-utilities (4 unit, 4 integration)
- ❌ containers/ModuleContainerResourceTest.java
- ❌ data/DebugTest.java
- ❌ data/ProtobufTestDataHelperTest.java
- ❌ data/ResourceLoadingTest.java

### engine/cli-register (2 tests)
- ❌ RegisterCommandTest.java
- ❌ service/ModuleRegistrationServiceTest.java

### engine/seed-config (3 tests)
- ❌ ConsulSeederCommandIT.java (probably should be unit test with IT variant)
- ❌ model/ConfigurationModelTest.java
- ❌ util/ConfigFileHandlerTest.java

### rokkon-protobuf (1 test)
- ❌ ProtoJarPackagingTest.java

## Next Steps

1. Start with engine/consul module as it has the most tests and is critical infrastructure
2. Apply base/unit/integration pattern systematically
3. Create test profiles as needed for each test's requirements
4. Track progress by updating this inventory