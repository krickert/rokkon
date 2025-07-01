#!/bin/bash
# Hybrid Development Script - Consul in container, Engine in dev mode
# This gives you hot reload with proper service discovery

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
CONSUL_CONTAINER="pipeline-consul-dev"
CONSUL_PORT=8500
ENGINE_HTTP_PORT=38082
ENGINE_GRPC_PORT=48082
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

echo -e "${GREEN}🚀 Pipeline Hybrid Development Setup${NC}"
echo "=================================="

# Function to check if a port is open
check_port() {
    nc -z localhost $1 2>/dev/null
}

# Function to wait for service
wait_for_service() {
    local service=$1
    local port=$2
    local max_attempts=30
    local attempt=1
    
    echo -n "Waiting for $service on port $port..."
    while ! check_port $port; do
        if [ $attempt -ge $max_attempts ]; then
            echo -e " ${RED}FAILED${NC}"
            echo "❌ $service failed to start after $max_attempts attempts"
            return 1
        fi
        echo -n "."
        sleep 1
        ((attempt++))
    done
    echo -e " ${GREEN}OK${NC}"
    return 0
}

# Step 1: Start Consul with host networking
echo -e "\n1️⃣  Starting Consul with host networking..."
if docker ps -q -f name=$CONSUL_CONTAINER > /dev/null 2>&1; then
    echo "Stopping existing Consul container..."
    docker stop $CONSUL_CONTAINER > /dev/null 2>&1 || true
    docker rm $CONSUL_CONTAINER > /dev/null 2>&1 || true
fi

echo "Starting Consul with host network mode..."
docker run -d \
    --name $CONSUL_CONTAINER \
    --network host \
    -v $SCRIPT_DIR/consul-data:/consul/data \
    hashicorp/consul:latest \
    agent -dev -ui -client=0.0.0.0 \
    -datacenter=dc1 \
    -node=consul-dev

wait_for_service "Consul" $CONSUL_PORT || exit 1
echo -e "${GREEN}✅ Consul started with host networking${NC}"
echo "   UI available at: http://localhost:$CONSUL_PORT/ui"

# Step 2: Create Consul service definition for the engine
echo -e "\n2️⃣  Creating Consul service definition for engine..."
cat > /tmp/engine-service.json << EOF
{
  "service": {
    "name": "pipeline-engine",
    "tags": ["pipeline", "engine", "grpc"],
    "address": "localhost",
    "port": $ENGINE_GRPC_PORT,
    "meta": {
      "service-type": "ENGINE",
      "grpc-port": "$ENGINE_GRPC_PORT",
      "http-port": "$ENGINE_HTTP_PORT",
      "version": "1.0.0"
    },
    "checks": [
      {
        "name": "Engine Health Check",
        "http": "http://localhost:$ENGINE_HTTP_PORT/q/health",
        "interval": "10s",
        "timeout": "5s"
      },
      {
        "name": "Engine gRPC Check",
        "grpc": "localhost:$ENGINE_GRPC_PORT",
        "grpc_use_tls": false,
        "interval": "10s",
        "timeout": "5s"
      }
    ],
    "connect": {
      "sidecar_service": {}
    }
  }
}
EOF

# Register the service definition
curl -X PUT http://localhost:$CONSUL_PORT/v1/agent/service/register -d @/tmp/engine-service.json
echo -e "${GREEN}✅ Engine service registered in Consul${NC}"

# Step 3: Seed Consul configuration
echo -e "\n3️⃣  Seeding Consul configuration..."
cd "$SCRIPT_DIR/cli/seed-engine-consul-config"

if [ ! -f "build/quarkus-app/quarkus-run.jar" ]; then
    echo "Building seed-engine-consul-config..."
    ./gradlew build -x test > /dev/null 2>&1
fi

echo "Seeding configuration..."
java -jar build/quarkus-app/quarkus-run.jar \
    -h localhost \
    -p $CONSUL_PORT \
    -c seed-data.json \
    --key config/application \
    --force 2>&1 | grep -E "(ERROR|WARN|INFO.*success|INFO.*completed)" || true

java -jar build/quarkus-app/quarkus-run.jar \
    -h localhost \
    -p $CONSUL_PORT \
    -c seed-data.json \
    --key config/dev \
    --force 2>&1 | grep -E "(ERROR|WARN|INFO.*success|INFO.*completed)" || true

echo -e "${GREEN}✅ Configuration seeded${NC}"

# Step 4: Build dependencies
echo -e "\n4️⃣  Building components..."
cd "$SCRIPT_DIR"
./gradlew :commons:protobuf:build :commons:interface:build :commons:util:build :commons:data-util:build -x test > /dev/null 2>&1
./gradlew :engine:consul:build :engine:validators:build -x test > /dev/null 2>&1
echo -e "${GREEN}✅ Dependencies built${NC}"

# Step 5: Start engine in dev mode
echo -e "\n5️⃣  Starting Pipeline Engine in dev mode..."
echo "=================================="
echo -e "${YELLOW}Configuration:${NC}"
echo "  HTTP Port: $ENGINE_HTTP_PORT"
echo "  gRPC Port: $ENGINE_GRPC_PORT"
echo "  Consul: localhost:$CONSUL_PORT"
echo "  Service Registration: Automatic via Consul"
echo ""
echo -e "${GREEN}Features:${NC}"
echo "  ✅ Live reload enabled"
echo "  ✅ Consul service discovery"
echo "  ✅ Health checks"
echo "  ✅ Service mesh ready"
echo ""
echo -e "${YELLOW}Available endpoints:${NC}"
echo "  Dashboard: http://localhost:$ENGINE_HTTP_PORT/"
echo "  Health: http://localhost:$ENGINE_HTTP_PORT/q/health"
echo "  OpenAPI: http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
echo "  Consul UI: http://localhost:$CONSUL_PORT/ui"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo "=================================="

# Start engine with proper configuration
cd "$SCRIPT_DIR"
CONSUL_HOST=localhost \
CONSUL_PORT=$CONSUL_PORT \
ENGINE_HOST=localhost \
QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
./gradlew :engine:pipestream:quarkusDev \
    -Dquarkus.http.port=$ENGINE_HTTP_PORT \
    -Dquarkus.grpc.server.port=$ENGINE_GRPC_PORT \
    -Dquarkus.consul-config.enabled=true

# Cleanup on exit
echo -e "\n${YELLOW}Shutting down...${NC}"
# Deregister service
curl -X PUT http://localhost:$CONSUL_PORT/v1/agent/service/deregister/pipeline-engine 2>/dev/null || true
echo "To stop Consul, run: docker stop $CONSUL_CONTAINER"