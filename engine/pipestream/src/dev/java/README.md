# Dev Mode Source Directory

This directory contains code that is **only** loaded when running the Pipeline Engine in Quarkus dev mode via `./gradlew dev`.

## Purpose

- Dev-specific utilities and helpers
- Mock services for local development
- Infrastructure management (Docker, Consul, etc.)
- Development-only endpoints and debugging tools

## Important Notes

1. Code in this directory is **never** included in production builds
2. It's automatically added to the classpath when running `quarkusDev`
3. Perfect for experimental features and development tools

## Current Dev Components

- `DockerAvailabilityChecker` - Validates Docker is running (currently skipped when managed by Gradle)
- `HostIPDetector` - Detects host IP for Docker container communication
- `PipelineDevModeInfrastructure` - Manages dev mode infrastructure
- `PipelineModule` - Dev mode module definitions

## Adding New Dev Features

Simply create new classes in this directory structure and they'll be available in dev mode only!