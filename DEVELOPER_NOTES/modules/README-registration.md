# Automated Module Registration

This document explains how to integrate the automated module registration feature into your module projects. This feature allows your module to automatically register with the Rokkon engine when it starts up.

## Overview

The automated registration process works as follows:

1. When a module container starts, it runs the `module-entrypoint.sh` script
2. The script starts the module application in the background
3. It waits for the module to be ready (health check)
4. It uses the `rokkon-cli` tool to register the module with the engine
5. If registration is successful, it keeps the module running
6. If registration fails, it retries according to the configured number of attempts

## Integration Steps

### Automated Setup

The easiest way to integrate automated registration is to use the provided setup script:

```bash
# From the repository root
./scripts/setup-module-registration.sh modules/your-module
```

This script will:
1. Add the necessary Gradle tasks to your build.gradle.kts
2. Update your Dockerfile.jvm to include the CLI tool and entrypoint script
3. Create or update your docker-build.sh script

### Manual Integration

If you prefer to manually integrate the registration, follow these steps:

#### 1. Update your build.gradle.kts

Add the following tasks to your module's `build.gradle.kts`:

```kotlin
// Copy module entrypoint script and CLI jar for Docker build
tasks.register<Copy>("copyModuleEntrypoint") {
    from(rootProject.file("scripts/module-entrypoint.sh"))
    into(layout.buildDirectory)
    rename { "module-entrypoint.sh" }
}

tasks.register<Copy>("copyRokkonCli") {
    from(project(":engine:cli-register").tasks.named("quarkusBuild").map { it.outputs.files.singleFile })
    into(layout.buildDirectory)
    rename { "rokkon-cli.jar" }
}

tasks.named("quarkusBuild") {
    finalizedBy("copyModuleEntrypoint", "copyRokkonCli")
}
```

### 2. Update your Dockerfile

Modify your Dockerfile to include the CLI tool and entrypoint script:

```dockerfile
# Install grpcurl for health checks
RUN curl -sSL https://github.com/fullstorydev/grpcurl/releases/download/v1.8.7/grpcurl_1.8.7_linux_x86_64.tar.gz | tar -xz -C /usr/local/bin

# Copy the CLI tool
COPY --chown=185 build/rokkon-cli.jar /deployments/rokkon-cli.jar

# Create a wrapper script for the CLI
RUN echo '#!/bin/bash' > /usr/local/bin/rokkon && \
    echo 'java -jar /deployments/rokkon-cli.jar "$@"' >> /usr/local/bin/rokkon && \
    chmod +x /usr/local/bin/rokkon

# Copy the entrypoint script
COPY --chown=185 build/module-entrypoint.sh /deployments/module-entrypoint.sh
RUN chmod +x /deployments/module-entrypoint.sh

# Make sure to expose the gRPC port
EXPOSE 9090

# Use the entrypoint script
ENTRYPOINT ["/deployments/module-entrypoint.sh"]
```

### 3. Update your docker-build.sh script

Ensure your docker-build script builds the CLI project first:

```bash
#!/bin/bash

# Build the CLI project first
echo "Building cli-register project..."
cd ../../
./gradlew :engine:cli-register:quarkusBuild

# Build the module application
echo "Building module..."
cd modules/your-module
./gradlew clean build

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/your-module:latest .
```

## Configuration

The registration process can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| MODULE_HOST | The host where the module is running | 0.0.0.0 |
| MODULE_PORT | The port where the module's gRPC server is listening | 9090 |
| ENGINE_HOST | The host where the engine is running | localhost |
| ENGINE_PORT | The port where the engine is listening | 8081 |
| CONSUL_HOST | The Consul host (optional) | "" |
| CONSUL_PORT | The Consul port (optional) | -1 |
| HEALTH_CHECK | Whether to perform a health check before registration | true |
| MAX_RETRIES | Maximum number of registration retry attempts | 3 |
| STARTUP_TIMEOUT | Timeout in seconds for module startup | 60 |
| CHECK_INTERVAL | Interval in seconds between health checks | 5 |

## Running the Container

When running the container, make sure to:

1. Map the gRPC port (default 9090)
2. Set the ENGINE_HOST environment variable to point to your engine
3. Set any other configuration variables as needed

Example:

```bash
docker run -i --rm \
  -p 9090:9090 \
  -e ENGINE_HOST=host.docker.internal \
  -e MAX_RETRIES=5 \
  rokkon/your-module:latest
```

## Non-Java Modules

For modules implemented in languages other than Java (Python, Rust, C#, etc.):

1. The entrypoint script needs to be modified to start your specific application
2. The health check mechanism remains the same (using grpcurl)
3. The CLI tool is still used for registration

Example modification for a Python module:

```bash
# Start the module in the background
echo "Starting module..."
python /app/main.py &
MODULE_PID=$!
```

## Troubleshooting

Common issues and solutions:

1. **Module fails to start**: Check the logs for startup errors
2. **Health check fails**: Ensure your module implements the gRPC health service
3. **Registration fails**: Verify the ENGINE_HOST and ENGINE_PORT are correct
4. **CLI tool not found**: Ensure the CLI jar was properly copied and the wrapper script created

## Example

See the `test-module` project for a complete example of a module with automated registration.
