# Echo Module - Reference Implementation

This is a working reference implementation of a Quarkus gRPC module with simple, self-contained proto definitions.

## Key Files

- `src/main/proto/echo.proto` - Simple proto definition with echo service
- `src/main/java/com/rokkon/echo/EchoServiceImpl.java` - Service implementation
- `src/test/java/com/rokkon/echo/EchoServiceTest.java` - Unit test with @GrpcClient injection
- `src/integrationTest/java/com/rokkon/echo/EchoServiceIT.java` - Integration test
- `build.gradle.kts` - Gradle build configuration

## Testing

All tests pass successfully:
- Unit tests: 3 tests passed
- Integration tests: 3 tests passed

## Key Patterns

1. Use `@GrpcService` annotation on implementation class
2. Implement the generated service interface
3. Use `@GrpcClient` for test injection
4. Keep proto definitions simple and self-contained
5. Integration tests create manual gRPC channel

## Configuration

- gRPC server runs on port 9090
- HTTP server runs on port 8080
- Uses separate gRPC server (legacy mode, current default)