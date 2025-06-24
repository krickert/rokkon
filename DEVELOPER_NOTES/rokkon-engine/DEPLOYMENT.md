# Rokkon Engine Deployment Guide

This document provides detailed instructions for DevOps engineers on how to deploy, configure, and manage the Rokkon Engine in various environments.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Docker Deployment](#docker-deployment)
   - [Development Mode](#development-mode)
   - [Production Mode](#production-mode)
4. [Configuration](#configuration)
   - [Environment Variables](#environment-variables)
   - [Application Configuration](#application-configuration)
5. [Customizing Docker Files](#customizing-docker-files)
6. [Monitoring and Management](#monitoring-and-management)
7. [Troubleshooting](#troubleshooting)

## Overview

The Rokkon Engine is the core orchestration component of the Rokkon pipeline system. It manages module registration, pipeline execution, and serves as the central control plane for document processing workflows.

## Prerequisites

- Docker and Docker Compose
- Java 21 or later (for local development)
- Gradle 8.0 or later (for local development)
- Access to Consul server (for service discovery and configuration)

## Docker Deployment

The Rokkon Engine provides several Docker deployment options following the Quarkus container standards.

### Development Mode

For development and testing, use the `Dockerfile.dev` which enables live coding and debugging:

```bash
# Build and start the development environment
cd rokkon-engine
docker-compose up -d
```

This will start:
- A Consul server for service discovery
- The Rokkon Engine in development mode with live coding enabled

#### Development Mode Features

- Live coding: Changes to the source code are automatically detected and applied
- Remote debugging on port 5005
- Exposed HTTP API on port 38090
- Exposed gRPC service on port 49000

### Production Mode

For production deployment, use the standard Quarkus container images:

```bash
# Build the application
./gradlew build

# Build the container image
docker build -f rokkon-engine/src/main/docker/Dockerfile.jvm -t rokkon/rokkon-engine:latest .

# Run the container
docker run -d -p 38090:38090 -p 49000:49000 \
  -e CONSUL_HOST=consul-host \
  -e CONSUL_PORT=8500 \
  rokkon/rokkon-engine:latest
```

#### Production Container Options

The Rokkon Engine provides several container options following Quarkus standards:

1. **JVM Mode** (`Dockerfile.jvm`): Standard JVM-based container
2. **Native Mode** (`Dockerfile.native`): GraalVM native image for faster startup and lower memory usage
3. **Native Micro Mode** (`Dockerfile.native-micro`): Minimal native image with distroless base
4. **Legacy JAR Mode** (`Dockerfile.legacy-jar`): Traditional fat JAR deployment

## Configuration

### Environment Variables

The following environment variables can be used to configure the Rokkon Engine:

| Variable | Description | Default |
|----------|-------------|---------|
| `CONSUL_HOST` | Consul server hostname | localhost |
| `CONSUL_PORT` | Consul server port | 8500 |
| `QUARKUS_PROFILE` | Quarkus profile to activate | dev |
| `ENGINE_HOST` | Engine hostname | localhost |
| `JAVA_OPTS_APPEND` | Additional JVM options | - |
| `GRPC_MAX_MESSAGE_SIZE` | Maximum gRPC message size | 4194304 |
| `GRPC_KEEPALIVE_TIME` | gRPC keepalive time in seconds | 30 |
| `GRPC_KEEPALIVE_TIMEOUT` | gRPC keepalive timeout in seconds | 10 |

### Application Configuration

The application configuration is managed through:

1. **application.yml**: Base configuration file
2. **application-dev.yml**: Development profile configuration
3. **application-docker-dev.yml**: Docker development profile configuration

Configuration is also read from Consul KV store at runtime from the path `rokkon/${CLUSTER_NAME:default}/config`.

## Customizing Docker Files

The Docker files are designed to be easily customizable by DevOps engineers. Here are the key files and how to customize them:

### Development Dockerfile

Location: `rokkon-engine/src/main/docker/Dockerfile.dev`

Common customizations:
- Adjust base image version
- Add additional dependencies
- Modify JVM options
- Change exposed ports

### Production Dockerfiles

Locations:
- `rokkon-engine/src/main/docker/Dockerfile.jvm`
- `rokkon-engine/src/main/docker/Dockerfile.native`
- `rokkon-engine/src/main/docker/Dockerfile.native-micro`
- `rokkon-engine/src/main/docker/Dockerfile.legacy-jar`

Common customizations:
- Adjust memory limits
- Configure GC options
- Add health check parameters
- Set up monitoring agents

### Docker Compose

Location: `rokkon-engine/docker-compose.yml`

Common customizations:
- Add additional services (e.g., monitoring tools)
- Configure networking
- Set up persistent volumes
- Adjust resource limits

## Monitoring and Management

The Rokkon Engine provides several endpoints for monitoring and management:

- **Health Check**: `/q/health` (HTTP)
- **Metrics**: `/q/metrics` (HTTP, Prometheus format)
- **OpenAPI**: `/q/openapi` (HTTP)
- **Swagger UI**: `/q/swagger-ui` (HTTP)
- **gRPC Health Check**: Health service (gRPC)

## Troubleshooting

### Common Issues

1. **Connection to Consul fails**:
   - Verify Consul is running and accessible
   - Check CONSUL_HOST and CONSUL_PORT environment variables
   - Ensure network connectivity between Engine and Consul

2. **Module registration fails**:
   - Check module is in whitelist
   - Verify module implements all required RPCs
   - Ensure module passes health check

3. **Pipeline execution issues**:
   - Verify all pipeline modules are healthy in Consul
   - Check logs for gRPC errors
   - Ensure network connectivity between engine and modules

### Logs

Logs are written to standard output in Docker containers. You can view them with:

```bash
docker logs rokkon-engine-dev
```

In development mode, logs are also written to `quarkus.log` in the project directory.