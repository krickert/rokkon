# Docker Development Environment

This directory contains Docker-based development configurations optimized for rapid development with Quarkus hot reload.

## Contents

- **docker-compose.dev.yml** - Docker Compose configuration for development
  - Runs Consul and all modules in containers
  - Configured for engine to run locally in dev mode
  - Modules connect to engine via `host.docker.internal`

- **dev-quarkus.sh** - Development environment management script
  - Starts/stops the development environment
  - Manages both Docker containers and local Quarkus dev mode
  - Supports minimal mode for faster startup

- **DEV_MODE_GUIDE.md** - Comprehensive guide for using the dev environment
  - Quick start instructions
  - Troubleshooting tips
  - Development workflow

## Quick Start

```bash
# Start everything (modules in Docker + engine in dev mode)
./dev-quarkus.sh

# Start only essential modules (echo + test)
./dev-quarkus.sh --minimal

# Stop everything
./dev-quarkus.sh --stop
```

## Key Differences from Production

1. **Engine runs locally** - Enables hot reload for rapid development
2. **Container names have -dev suffix** - Avoids conflicts with production containers
3. **Modules use host.docker.internal** - Connects to host machine's engine
4. **Separate Consul data volume** - Isolated from production data

## Related Directories

- `../compose/` - Production-like Docker Compose configurations
- `../../engine/pipestream/` - Engine source code (run in dev mode)