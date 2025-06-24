# gRPC Routing Tests

This directory contains tests for the gRPC routing mechanism within the Rokkon Engine. These tests verify that the engine can correctly route requests between modules using gRPC.

## Test Classes

### GrpcTransportHandlerTest

Tests the `GrpcTransportHandler` class, which is responsible for routing gRPC requests between modules. It verifies:

1. **Configuration Validation**: Tests that the handler correctly identifies valid and invalid configurations.
   - `testCanHandleWithValidConfig`: Verifies that the handler accepts configurations with a valid gRPC service name.
   - `testCanHandleWithInvalidConfig`: Verifies that the handler rejects configurations without a gRPC service name.

2. **Request Routing**: Tests that the handler correctly routes requests to the appropriate service.
   - `testRouteRequestSuccessfully`: Verifies that requests are routed to the correct service and responses are returned.

3. **Error Handling**: Tests that the handler correctly handles error scenarios.
   - `testRouteRequestWithServiceDiscoveryFailure`: Verifies that service discovery failures are properly propagated.
   - `testRouteRequestWithProcessingFailure`: Verifies that processing failures in the target service are properly propagated.

### EventDrivenRouterTest

Tests the `EventDrivenRouter` class, which orchestrates message routing between pipeline steps using pluggable transport mechanisms. It verifies:

1. **Transport Selection**: Tests that the router correctly selects the appropriate transport handler.
   - `testRouteRequestUsingGrpcTransport`: Verifies that gRPC requests are routed using the gRPC transport handler.

2. **Multi-Destination Routing**: Tests that the router can route to multiple destinations.
   - `testRouteStreamToMultipleDestinations`: Verifies that streams can be routed to multiple destinations using different transport types.

3. **Partial Failure Handling**: Tests that the router correctly handles partial failures.
   - `testRouteStreamWithPartialFailure`: Verifies that when routing to multiple destinations, failures in one destination don't affect others.

4. **Error Handling**: Tests that the router correctly handles error scenarios.
   - `testRouteRequestWithNoHandlerAvailable`: Verifies that the router fails appropriately when no handler is available for a step.

## Integration with Other Tests

These tests complement the existing integration tests:

- `ConsulGrpcHealthCheckNetworkTest`: Tests that Consul can perform gRPC health checks on containers sharing a Docker network.
- `FullPipelineE2EIT`: Tests the complete pipeline setup and execution flow, including gRPC communication between modules.

Together, these tests provide comprehensive coverage of the gRPC routing mechanism within the Rokkon Engine.