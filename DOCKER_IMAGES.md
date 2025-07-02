# Rokkon Docker Images

## Overview

This document describes the Docker images for all Rokkon modules and how to build, run, and deploy them.

## Built Images

All modules have been successfully built as Docker images:

| Module | Image | Size | Description |
|--------|-------|------|-------------|
| test-module | `rokkon/test-module:latest` | 460MB | Testing and validation module |
| echo | `rokkon/echo:latest` | 465MB | Simple echo service for testing |
| chunker | `rokkon/chunker:latest` | 462MB | Text chunking service |
| parser | `rokkon/parser:latest` | 509MB | Document parsing service |
| embedder | `rokkon/embedder:latest` | 490MB | Text embedding service |

## Building Images

### Build All Images
```bash
# Build all module images locally
./simple-docker-build.sh

# Build with parallel processing
./docker-build-all.sh

# Build with custom registry/tag
./docker-build-all.sh --registry myregistry.com --namespace mycompany --tag v1.0.0
```

### Build Individual Module
```bash
# Build a specific module
./gradlew :modules:echo:quarkusBuild
cd modules/echo
docker build -f src/main/docker/Dockerfile.jvm -t rokkon/echo:latest .
```

## Running Images

### Run Individual Module
```bash
# Run echo module
docker run --rm -p 8080:8080 -p 9000:9000 rokkon/echo:latest

# Run with environment variables
docker run --rm \
  -p 8080:8080 \
  -p 9000:9000 \
  -e QUARKUS_LOG_LEVEL=DEBUG \
  -e MODULE_NAME=echo \
  rokkon/echo:latest
```

### Run All Modules with Docker Compose
```bash
# Start all modules
docker-compose -f docker-compose-modules.yml up -d

# View logs
docker-compose -f docker-compose-modules.yml logs -f

# Stop all modules
docker-compose -f docker-compose-modules.yml down
```

## Pushing to Registry

### Prerequisites
1. Set your Docker registry credentials:
```bash
export DOCKER_REGISTRY=docker.io  # or your registry
export DOCKER_NAMESPACE=your-username  # your namespace
export DOCKER_TAG=latest  # or specific version
```

2. Login to your registry:
```bash
docker login $DOCKER_REGISTRY
```

### Push All Images
```bash
# Push all images to registry
./push-docker-images.sh
```

### Push Individual Image
```bash
# Tag and push a single image
docker tag rokkon/echo:latest myregistry.com/mycompany/echo:latest
docker push myregistry.com/mycompany/echo:latest
```

## Port Mappings

Each module exposes two ports:
- **HTTP Port (8080)**: REST API and health checks
- **gRPC Port (9000)**: gRPC services

Default port mappings when using docker-compose:

| Module | HTTP Port | gRPC Port |
|--------|-----------|-----------|
| test-module | 8080 | 9000 |
| echo | 8081 | 9001 |
| chunker | 8082 | 9002 |
| parser | 8083 | 9003 |
| embedder | 8084 | 9004 |

## Health Checks

All modules provide health endpoints:

```bash
# HTTP health check
curl http://localhost:8080/q/health

# HTTP liveness
curl http://localhost:8080/q/health/live

# HTTP readiness
curl http://localhost:8080/q/health/ready

# gRPC health check
grpcurl -plaintext localhost:9000 grpc.health.v1.Health/Check
```

## Environment Variables

Common environment variables for all modules:

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_HTTP_PORT` | HTTP server port | 8080 |
| `QUARKUS_GRPC_SERVER_PORT` | gRPC server port | 9000 |
| `QUARKUS_LOG_LEVEL` | Log level | INFO |
| `MODULE_NAME` | Module identifier | (module specific) |
| `MODULE_VERSION` | Module version | 1.0.0 |

Module-specific variables:

### Chunker
- `CHUNKER_MAX_CHUNK_SIZE`: Maximum chunk size (default: 1000)
- `CHUNKER_OVERLAP_SIZE`: Overlap between chunks (default: 100)

### Parser
- `PARSER_MAX_FILE_SIZE`: Maximum file size to parse (default: 100MB)

### Embedder
- `EMBEDDER_MODEL`: Embedding model to use (default: sentence-transformers/all-MiniLM-L6-v2)

## Troubleshooting

### View Container Logs
```bash
docker logs rokkon-echo
docker logs -f rokkon-echo  # Follow logs
```

### Access Container Shell
```bash
docker exec -it rokkon-echo /bin/bash
```

### Check Resource Usage
```bash
docker stats rokkon-echo
```

### Clean Up
```bash
# Remove all rokkon images
docker images "rokkon/*" -q | xargs docker rmi

# Remove stopped containers
docker container prune

# Full cleanup
docker system prune -a
```

## Next Steps

1. **Deploy to Kubernetes**: Use the generated images with Kubernetes deployments
2. **Add to CI/CD**: Integrate image building into your CI/CD pipeline
3. **Security Scanning**: Scan images for vulnerabilities before deployment
4. **Image Optimization**: Consider using native images for smaller size and faster startup