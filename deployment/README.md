# Rokkon Platform Deployment

This directory contains deployment configurations and scripts for the Rokkon platform.

## Quick Start

### Local Development Deployment

To deploy the entire Rokkon platform locally:

```bash
# From project root
./deploy.sh
```

This will:
1. Build all components (engine, CLI tool, test-module)
2. Create Docker images
3. Start Consul, Engine, and Test Module with docker-compose
4. Automatically register the test-module with the engine

### Production Deployment to Registry

To build and push images to the nas.rokkon.com:5000 registry:

```bash
# From project root
./deploy-to-registry.sh
```

This will:
1. Build all components
2. Tag images for the registry
3. Push images to nas.rokkon.com:5000
4. Update docker-compose.yml to use registry images

## Architecture

The deployment consists of three main components:

### 1. Consul
- Service discovery and configuration management
- Runs on port 8500 (HTTP API & UI)
- Automatically seeded with default configuration by the engine

### 2. Rokkon Engine
- Core orchestration engine
- Dashboard UI on port 8080
- gRPC API on port 8081
- Automatically seeds Consul configuration on startup

### 3. Test Module
- Example module implementation
- gRPC service on port 9090
- Automatically registers with the engine on startup

## Configuration

### Environment Variables

#### Engine Configuration
- `QUARKUS_CONSUL_CONFIG_AGENT_HOST_PORT`: Consul connection (default: consul:8500)
- `ROKKON_ENGINE_NAME`: Engine instance name
- `ROKKON_ENGINE_VERSION`: Engine version

#### Module Configuration
- `MODULE_HOST`: Module bind address (default: 0.0.0.0)
- `MODULE_PORT`: Module gRPC port (default: 9090)
- `ENGINE_HOST`: Engine host for registration
- `ENGINE_PORT`: Engine gRPC port for registration
- `MAX_RETRIES`: Registration retry attempts (default: 5)

## Files

- `docker-compose.yml` - Local development deployment
- `docker-compose.prod.yml` - Production deployment using registry images
- `deploy.sh` - Main deployment script
- `deploy-to-registry.sh` - Registry deployment wrapper

## Access Points

After deployment, you can access:

- **Rokkon Dashboard**: http://localhost:8080
- **Consul UI**: http://localhost:8500
- **Engine gRPC**: localhost:8081
- **Test Module gRPC**: localhost:9090

## Monitoring

View logs:
```bash
docker-compose logs -f
```

Check service health:
```bash
docker-compose ps
```

View test-module registration:
```bash
docker logs rokkon-test-module
```

## Stopping the Platform

```bash
docker-compose down
```

To also remove volumes:
```bash
docker-compose down -v
```

## Troubleshooting

### Module Registration Issues
1. Check module logs: `docker logs rokkon-test-module`
2. Verify engine is healthy: `docker-compose ps`
3. Check Consul UI for registered services

### Consul Connection Issues
1. Ensure Consul is running: `docker-compose ps consul`
2. Check Consul logs: `docker logs rokkon-consul`
3. Verify network connectivity between containers

### Build Failures
1. Ensure all prerequisites are installed (Docker, Docker Compose, Java)
2. Check that Gradle build succeeds: `./gradlew build`
3. Verify Docker daemon is running