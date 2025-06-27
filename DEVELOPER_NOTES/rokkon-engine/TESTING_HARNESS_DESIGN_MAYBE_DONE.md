# Rokkon Engine Architecture and Implementation Plan

## Test Harness Documentation

### Overview
The test-module now includes a bidirectional streaming gRPC test harness that enables sophisticated integration testing. This harness allows tests to send commands to the module and receive real-time events back, facilitating complex test scenarios.

### Architecture

#### Proto Definition (test_harness.proto)
```proto
service TestHarness {
    // Bidirectional streaming for real-time test orchestration
    rpc ExecuteTestStream(stream TestCommand) returns (stream TestEvent);
    
    // Single command execution
    rpc ExecuteTest(TestCommand) returns (TestResult);
    
    // Module status monitoring
    rpc GetModuleStatus(google.protobuf.Empty) returns (ModuleStatus);
}
```

#### Key Components
1. **TestHarnessServiceImpl** - Main service implementation using Mutiny for reactive streaming
2. **TestCommand** - Commands sent to the harness (CHECK_HEALTH, PROCESS_DOCUMENT, CONFIGURE_MODULE, etc.)
3. **TestEvent** - Events streamed back (HEALTH_CHECKED, DOCUMENT_PROCESSED, ERROR_OCCURRED, etc.)
4. **TestHarnessResource** - REST endpoint at `/test-harness/status` for HTTP access

### Usage Patterns

#### Unit Testing (@QuarkusTest)
```java
@QuarkusTest
class TestHarnessServiceTest {
    @Inject
    @GrpcClient
    TestHarness testHarness;
    
    @Test
    void testStreamingCommands() {
        Multi<TestCommand> commands = Multi.createFrom().items(
            createHealthCheckCommand(),
            createDocumentProcessCommand("test-doc-1"),
            createVerifyRegistrationCommand()
        );
        
        List<TestEvent> events = testHarness.executeTestStream(commands)
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));
        
        // Assert on received events
    }
}
```

#### Integration Testing (@QuarkusIntegrationTest)
```java
@QuarkusIntegrationTest
class TestHarnessServiceIT {
    private ManagedChannel channel;
    private TestHarnessClient testHarnessClient;
    
    @BeforeEach
    void setup() {
        // Integration tests use manual channel creation
        int port = 49094; // Port from application.yml
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        testHarnessClient = new TestHarnessClient("TestHarness", channel, (name, stub) -> stub);
    }
}
```

### Test Commands Available
- **CHECK_HEALTH** - Verify module health
- **PROCESS_DOCUMENT** - Test document processing with configurable success/failure
- **CONFIGURE_MODULE** - Dynamic module configuration during tests
- **VERIFY_REGISTRATION** - Confirm module registration details
- **SIMULATE_ERROR** - Test error handling paths
- **RESET_MODULE** - Reset module to default state

### Integration Test Patterns

#### Black-Box Testing
The test harness enables true black-box testing where:
1. Tests send commands via gRPC streaming
2. Module processes commands and emits events
3. Tests observe state changes through:
   - Streamed events from the harness
   - Consul state queries
   - Kafka message inspection (when integrated)
   - REST health endpoints

#### Concurrent Testing
The streaming nature allows testing concurrent operations:
```java
// Multiple clients can stream simultaneously
testHarnessClient.executeTestStream(stream1Commands)...
testHarnessClient.executeTestStream(stream2Commands)...
```

### Benefits
1. **Real-time Feedback** - Streaming provides immediate event notification
2. **Complex Scenarios** - Chain multiple commands to create sophisticated test cases
3. **State Verification** - Module can report internal state through events
4. **Error Testing** - Explicit error simulation capabilities
5. **Performance Testing** - Measure processing times through event timestamps

### Future Enhancements
- Kafka integration for event publishing
- Metrics collection during tests
- Load testing capabilities
- Module coordination for multi-module tests


