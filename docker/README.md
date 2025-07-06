# Docker Deployment Configurations

This directory contains all Docker-related deployment configurations for the Pipeline Engine project.

## Directory Structure

```
docker/
├── compose/                      # Docker Compose configurations
│   ├── docker-compose.yml        # Base engine + echo module
│   ├── docker-compose.all-modules.yml  # All modules deployed
│   ├── .env                      # Default environment variables
│   ├── .env.all-modules          # Environment for all modules
│   └── modules/                  # Individual module deployments
│       ├── test-module.yml       # Test module only
│       └── .env.test-module      # Test module environment
└── README.md                     # This file
```

## Usage

### Deploy Engine + Echo Module
```bash
cd docker/compose
docker compose up -d
```

### Deploy All Modules
```bash
cd docker/compose
docker compose -f docker-compose.all-modules.yml --env-file .env.all-modules up -d
```

### Deploy Test Module Only
```bash
cd docker/compose/modules
docker compose -f test-module.yml --env-file .env.test-module up -d
```

### Stop Everything
```bash
docker compose down
```

## Port Conventions

- **Engine**: HTTP 39001, gRPC 49001
- **All Modules**: HTTP 39100, gRPC 39100

## Sidecar Pattern

Each service runs with a Consul sidecar agent:
- Applications share network namespace with their sidecar
- Port mappings are on the sidecar container
- Registration uses the sidecar's container name as the host

## Building Images

Before deploying, build the required Docker images:

```bash
# Build engine
./gradlew :engine:pipestream:imageBuild -Dquarkus.container-image.build=true

# Build modules
./gradlew :modules:echo:imageBuild -Dquarkus.container-image.build=true
./gradlew :modules:test-module:imageBuild -Dquarkus.container-image.build=true
```

## Accessing Services

- **Consul UI**: http://localhost:8500
- **Engine Health**: http://localhost:39001/q/health
- **Engine Swagger**: http://localhost:39001/swagger-ui
- **Echo Module**: http://localhost:39100/q/health
- **Test Module**: http://localhost:39100/q/health