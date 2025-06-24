# Follow-Up Tasks for Rokkon Engine Integration Tests

## 1. Implement Comprehensive Docker Setup for Integration Tests

### Description
Create a comprehensive Docker setup for integration tests that includes Consul, the Rokkon Engine, and any required test modules, all running in the same network with the correct hostnames and ports.

### Requirements
- See the `INTEGRATION_TEST_SETUP.md` document for detailed requirements and implementation steps.
- The setup should be implemented as a custom Testcontainers resource that extends `QuarkusTestResourceLifecycleManager`.
- The resource should handle building and starting the required containers, creating a Docker network, and configuring the containers with the correct hostnames and ports.
- Leverage the existing `integration-test/docker-compose.yml` file as a reference for container configuration.

### Acceptance Criteria
- Integration tests can be run without manual setup
- All containers start up correctly and can communicate with each other
- Tests pass consistently
- CI/CD pipeline can execute the tests automatically

## 2. Add Test Module for Pipeline Testing

### Description
Configure the existing test-module for testing pipeline definitions in integration tests.

### Requirements
- Use the existing test-module in the `modules/test-module` directory
- Configure it with appropriate environment variables for testing
- Ensure it registers with the engine automatically
- Document common testing scenarios and configuration options

### Acceptance Criteria
- The module can be used in pipeline definitions
- It processes data correctly
- It reports status and metrics to the engine
- It can be configured for different testing scenarios (validation, error simulation, etc.)

## 3. Add Seed Configuration for Integration Tests

### Description
Implement automatic seeding of Consul configuration for integration tests using the existing seed-config tool.

### Requirements
- Use the `seed-config` tool in the `engine/seed-config` directory
- Create test-specific configuration files
- Integrate seeding into the test resource lifecycle
- Ensure idempotent operation (can be run multiple times safely)

### Acceptance Criteria
- Consul is seeded with the required configuration before tests run
- Configuration is cleaned up after tests
- Tests can run without manual configuration
- Seeding process is logged for debugging

## 4. Improve Test Stability and Performance

### Description
Optimize the Docker setup for faster test execution and improved stability.

### Requirements
- Minimize container build time using layer caching
- Implement caching strategies for Docker images
- Ensure proper cleanup of resources
- Add health checks to ensure services are ready before tests start

### Acceptance Criteria
- Tests run faster (target: under 5 minutes for full suite)
- Tests are more stable (target: 99% pass rate)
- Resource usage is minimized
- Failed tests provide clear diagnostic information