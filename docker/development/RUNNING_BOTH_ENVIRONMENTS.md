# Running Both Production and Development Environments

Yes, you can run both environments simultaneously! They use different networks and container names.

## Option 1: Run Dev on Different Ports (Recommended)

1. Start production environment first:
```bash
cd docker/compose
docker-compose -f docker-compose.all-modules.yml up -d
```

2. Start dev environment with different ports:
```bash
cd docker/development
# Use the .env.dev file for different ports
docker-compose --env-file .env.dev -f docker-compose.dev.yml up -d
```

3. Access points:
- **Production Engine**: http://localhost:39000
- **Dev Engine**: http://localhost:40000 (when running in Quarkus dev mode)
- **Production Consul**: http://localhost:8500
- **Dev Consul**: http://localhost:9500

## Option 2: Run Only One Set of Modules

If you don't need both sets of modules:

1. Start production Consul + Engine only:
```bash
cd docker/compose
docker-compose -f docker-compose.yml up -d
```

2. Start dev modules:
```bash
cd docker/development
docker-compose -f docker-compose.dev.yml up -d
```

3. Run engine in dev mode locally:
```bash
cd engine/pipestream
./gradlew quarkusDev -Dquarkus.http.port=40000 -Dquarkus.grpc.server.port=50000
```

## Network Isolation

The environments are completely isolated:
- **Production Network**: `compose_pipeline-network`
- **Development Network**: `development_pipeline-dev-network`
- **No cross-communication** between environments

## Container Names

All containers have different names:
- Production: `consul-server`, `echo-module`, etc.
- Development: `consul-server-dev`, `echo-module-dev`, etc.

## Tips

1. **Check what's running**:
```bash
docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Networks}}"
```

2. **Stop specific environment**:
```bash
# Stop dev
cd docker/development
docker-compose -f docker-compose.dev.yml down

# Stop prod
cd docker/compose
docker-compose -f docker-compose.all-modules.yml down
```

3. **View logs by environment**:
```bash
# Dev logs
docker logs echo-module-dev

# Prod logs
docker logs echo-module
```