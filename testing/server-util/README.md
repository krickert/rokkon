# Testing Server Utilities

This module provides heavy Quarkus/Docker testing utilities for Rokkon server components.

## Features

- Docker and Testcontainers integration utilities
- Quarkus test utilities for server components
- gRPC testing helpers
- Common test fixtures and utilities for integration testing

## Usage

Add this dependency to your server component's test dependencies:

```kotlin
testImplementation(project(":testing:server-util"))
```

## Dependencies

This module uses the server BOM for dependency management and provides:
- Testcontainers for Docker-based testing
- Quarkus test utilities (without requiring the Quarkus plugin)
- gRPC testing utilities
- Common assertion and mocking libraries