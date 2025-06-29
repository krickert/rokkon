# Mock Engine Project Plan - Priority 1 for Connector Server Development

## Overview

This document outlines the plan to create a lightweight mock engine that will be the **first deliverable** in our connector server development. This mock engine will:
1. Extract and enhance the existing mock from filesystem-crawler
2. Support both legacy ConnectorEngine interface and new direct pipeline communication
3. Serve as the testing foundation for concurrent connector development
4. Be packaged as a Docker container for easy testing

## Purpose and Priority

**This is Phase 0 of the connector server development** - creating the testing infrastructure that enables:
- Concurrent connector development while the connector server is being built
- Testing of existing connectors (filesystem-crawler) with the mock
- Testing of the new connector-server when it's ready
- Validation of the direct pipeline communication pattern

## Current State

Currently, the mock connector engine is implemented in two places:
1. `MockConnectorEngine` in the test directory of the filesystem-crawler project
2. `RealMockConnectorEngine` in the integrationTest directory of the filesystem-crawler project

Both implementations provide a mock of the `ConnectorEngine` interface, which is a gRPC service defined in the `connector_service.proto` file.

## New Project Structure

The mock engine will be created as a permanent testing fixture under `rokkon-engine-new/pipestream-mock`:

```
rokkon-engine-new/
├── pipestream-mock/   # Permanent mock engine for testing
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── rokkon/
│   │   │   │           └── mock/
│   │   │   │               ├── engine/
│   │   │   │               │   ├── MockConnectorEngine.java         # Legacy ConnectorEngine interface
│   │   │   │               │   ├── MockPipeStepProcessor.java       # New direct pipeline interface
│   │   │   │               │   ├── MockEngineServer.java            # Unified gRPC server
│   │   │   │               │   └── RequestStore.java                # Shared request storage
│   │   │   │               ├── pipeline/
│   │   │   │               │   ├── MockModuleRegistry.java          # Mock module discovery
│   │   │   │               │   └── MockPipelineRouter.java          # Mock routing logic
│   │   │   │               └── api/
│   │   │   │                   └── MockEngineResource.java          # REST API for test control
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── META-INF/
│   │   │           └── resources/
│   │   │               └── index.html                               # Simple UI for debugging
│   │   └── test/
│   │       └── java/
│   │           └── com/
│   │               └── rokkon/
│   │                   └── mock/
│   │                       └── engine/
│   │                           ├── MockConnectorEngineTest.java
│   │                           └── MockPipeStepProcessorTest.java
│   ├── Dockerfile
│   ├── docker-compose.yml    # Includes Consul for service discovery testing
│   ├── build.gradle.kts
│   └── README.md
├── pipestream/        # Future real pipeline engine (created later)
└── connector/         # Connector server (uses pipestream-mock for testing)
```

### Key Components

1. **Dual Interface Support**:
   - **MockConnectorEngine.java**: Implements the legacy `ConnectorEngine` interface for testing existing connectors
   - **MockPipeStepProcessor.java**: Implements `PipeStepProcessor` for direct pipeline communication testing

2. **Shared Infrastructure**:
   - **RequestStore.java**: Stores all requests (from both interfaces) for verification
   - **MockEngineServer.java**: Unified gRPC server hosting both services

3. **Pipeline Simulation**:
   - **MockModuleRegistry.java**: Simulates Consul service discovery for modules
   - **MockPipelineRouter.java**: Simulates routing between pipeline steps

4. **Test Control**:
   - **MockEngineResource.java**: REST API for configuring mock behavior and retrieving test data

5. **Configuration**:
   - **application.yml**: Supports both legacy and new configurations
   - **docker-compose.yml**: Includes mock engine + Consul for realistic testing

## Dependencies

The mock-engine project will have the following dependencies:

```kotlin
dependencies {
    // Quarkus dependencies
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-config-yaml")
    
    // Rokkon dependencies
    implementation("com.rokkon.pipeline:commons-protobuf")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
}
```

## Implementation Details

### MockConnectorEngine

The `MockConnectorEngine` will be based on the current `RealMockConnectorEngine` implementation, with the following enhancements:

1. **Request Storage**: Store received requests for later verification.
2. **Response Customization**: Allow customizing responses through a response function.
3. **Expectations**: Support setting expectations for the number of requests expected.
4. **Verification**: Provide methods to verify that expectations were met.
5. **REST API Integration**: Expose functionality through a REST API for remote interaction.

### MockConnectorEngineServer

The `MockConnectorEngineServer` will be responsible for starting the gRPC server for the mock engine. It will:

1. Register the `MockConnectorEngine` as a gRPC service.
2. Configure the server port and other settings.
3. Start the server when the application starts.

### REST API

The mock engine will expose a comprehensive REST API for test control:

#### General Endpoints:
- `GET /api/mock/status`: Get the overall status of the mock engine
- `POST /api/mock/reset`: Reset all mock state
- `GET /api/mock/health`: Health check endpoint

#### ConnectorEngine Interface (Legacy):
- `GET /api/mock/connector/requests`: Get all ConnectorRequest messages received
- `GET /api/mock/connector/requests/count`: Get count of connector requests
- `POST /api/mock/connector/reset/{count}`: Reset with expected request count
- `POST /api/mock/connector/response`: Configure custom ConnectorResponse
- `GET /api/mock/connector/streams`: Get all active stream IDs

#### PipeStepProcessor Interface (New):
- `GET /api/mock/pipeline/requests`: Get all ProcessRequest messages received
- `GET /api/mock/pipeline/requests/count`: Get count of pipeline requests
- `POST /api/mock/pipeline/response`: Configure custom ProcessResponse
- `GET /api/mock/pipeline/modules`: List registered mock modules
- `POST /api/mock/pipeline/modules`: Register a new mock module
- `DELETE /api/mock/pipeline/modules/{name}`: Unregister a mock module

#### Test Scenarios:
- `POST /api/mock/scenario/{name}`: Load a predefined test scenario
- `GET /api/mock/scenarios`: List available test scenarios

## Docker Container

The mock engine will be packaged as a Docker container:

```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-21:latest

ENV LANGUAGE='en_US:en'

COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 build/quarkus-app/*.jar /deployments/
COPY --chown=185 build/quarkus-app/app/ /deployments/app/
COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
EXPOSE 49000

USER 185

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
```

## Changes to Filesystem-Crawler

To use the standalone mock engine, the filesystem-crawler project will need the following changes:

1. **Remove the existing mock implementations**: Delete `MockConnectorEngine`, `MockConnectorEngineProducer`, `RealMockConnectorEngine`, and `RealMockConnectorEngineProducer`.

2. **Update integration tests**: Modify integration tests to use the containerized mock engine:

```java
@QuarkusTest
public class FilesystemCrawlerIntegrationTest {

    @Test
    void testCrawlWithMockEngine() {
        // Configure the mock engine via REST API
        given()
            .when()
            .post("http://localhost:8081/api/mock/reset/4") // Expect 4 documents
            .then()
            .statusCode(200);
        
        // Trigger the crawl
        given()
            .when()
            .post("/api/crawler/crawl")
            .then()
            .statusCode(200);
        
        // Wait for processing to complete
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            Response response = given()
                .when()
                .get("http://localhost:8081/api/mock/requests/count")
                .thenReturn();
            
            return response.getBody().asString().equals("4");
        });
        
        // Verify the requests
        Response response = given()
            .when()
            .get("http://localhost:8081/api/mock/requests")
            .thenReturn();
        
        List<ConnectorRequest> requests = response.getBody().as(new TypeRef<List<ConnectorRequest>>() {});
        
        // Verify the requests as before
        // ...
    }
}
```

3. **Update application.yml**: Configure the connector to use the mock engine:

```yaml
quarkus:
  grpc:
    clients:
      connector-engine:
        host: ${ENGINE_HOST:localhost}
        port: ${ENGINE_PORT:49000}
```

## Integration Testing Approach

The integration testing approach will involve:

1. **Starting the mock engine container**: Before running integration tests, start the mock engine container:

```bash
docker run -d --name mock-engine -p 8081:8080 -p 49000:49000 rokkon/mock-engine:latest
```

2. **Configuring the filesystem-crawler**: Configure the filesystem-crawler to connect to the mock engine:

```bash
export ENGINE_HOST=localhost
export ENGINE_PORT=49000
```

3. **Running the integration tests**: Run the integration tests, which will use the mock engine via its REST API and gRPC interface.

4. **Stopping the mock engine container**: After the tests complete, stop the mock engine container:

```bash
docker stop mock-engine
docker rm mock-engine
```

This approach can be automated in a CI/CD pipeline using scripts or Docker Compose.

## Benefits

1. **Isolation**: The mock engine is completely isolated from the connectors, allowing independent development and testing.
2. **Reusability**: The mock engine can be used by any connector, not just the filesystem-crawler.
3. **Containerization**: The mock engine can be deployed as a container, making it easy to use in different environments.
4. **Testability**: Connectors can be tested against a consistent mock engine implementation.
5. **Scalability**: The mock engine can be scaled independently of the connectors.

## Challenges

1. **Complexity**: Adds complexity to the development and testing process.
2. **Maintenance**: Requires maintaining an additional project.
3. **Synchronization**: Requires keeping the mock engine in sync with changes to the real engine interface.
4. **Network Issues**: Introduces potential network-related issues in testing.
5. **Container Management**: Requires managing Docker containers for testing.

## Integration with Connector Server Development

This mock engine is **Phase 0** of the connector server development plan. It enables:

### Immediate Benefits:
1. **Parallel Development**: You can develop connectors while the connector server is being built
2. **Testing Foundation**: Provides immediate testing capability for filesystem-crawler
3. **Validation**: Validates the direct pipeline communication pattern before full implementation
4. **Migration Path**: Tests can be written against the mock and work with the real system later

### Development Timeline:

#### Week 1 - Mock Engine (Phase 0):
- Day 1-2: Project setup and structure
- Day 3-4: Extract and enhance MockConnectorEngine from filesystem-crawler
- Day 4-5: Add MockPipeStepProcessor for new interface
- Day 5: Docker setup and integration testing

#### Concurrent Development:
While Phase 0 is being completed, you can:
- Start developing new connectors using the mock
- Test filesystem-crawler with the containerized mock
- Validate connector patterns and designs

## Implementation Steps

### Step 1: Project Setup (Day 1)
1. Create `rokkon-engine-new/pipestream-mock` directory structure
2. Set up build.gradle.kts with dependencies
3. Configure application.yml for both interfaces
4. Add to root settings.gradle.kts

### Step 2: Extract Mock Engine (Day 2-3)
1. Copy `RealMockConnectorEngine` from filesystem-crawler
2. Enhance with RequestStore for better test verification
3. Add REST API endpoints for connector interface
4. Create unit tests

### Step 3: Add Pipeline Interface (Day 3-4)
1. Implement MockPipeStepProcessor
2. Add pipeline-specific REST endpoints
3. Create MockModuleRegistry for service discovery
4. Add routing simulation

### Step 4: Docker and Testing (Day 4-5)
1. Create multi-stage Dockerfile
2. Set up docker-compose.yml with Consul
3. Test with filesystem-crawler
4. Create integration test examples

### Step 5: Documentation and Handoff
1. Create comprehensive README
2. Document all REST endpoints
3. Provide example test scenarios
4. Create migration guide for filesystem-crawler

## Success Criteria

1. **Filesystem-crawler works with the mock**: Existing tests pass with containerized mock
2. **Both interfaces functional**: Can receive and verify both ConnectorRequest and ProcessRequest
3. **Docker deployment works**: Container runs standalone with docker-compose
4. **REST API complete**: All endpoints working for test control
5. **Documentation complete**: Clear instructions for connector developers