#!/bin/bash
# dev-quarkus.sh - Start Quarkus in dev mode with Docker modules

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENGINE_DIR="$PROJECT_ROOT/engine/pipestream"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Rokkon Development Environment${NC}"

# Function to check if port is available
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 1
    else
        return 0
    fi
}

# Function to wait for service
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}Waiting for $service_name to be ready...${NC}"
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "$url" > /dev/null; then
            echo -e "${GREEN}$service_name is ready!${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    echo -e "${RED}$service_name failed to start after $max_attempts attempts${NC}"
    return 1
}

# Parse command line arguments
MODULES_ONLY=false
ENGINE_ONLY=false
STOP=false
MINIMAL=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --modules-only)
            MODULES_ONLY=true
            shift
            ;;
        --engine-only)
            ENGINE_ONLY=true
            shift
            ;;
        --stop)
            STOP=true
            shift
            ;;
        --minimal)
            MINIMAL=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --modules-only    Start only Docker modules (no engine)"
            echo "  --engine-only     Start only engine in dev mode (assumes modules running)"
            echo "  --minimal         Start only echo and test modules (skip chunker/embedder)"
            echo "  --stop            Stop all services"
            echo "  -h, --help        Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Stop services if requested
if [ "$STOP" = true ]; then
    echo -e "${YELLOW}Stopping all services...${NC}"
    cd "$SCRIPT_DIR"
    docker-compose -f docker-compose.dev.yml down
    
    # Also try to stop any local engine
    if [ -f "$ENGINE_DIR/engine.pid" ]; then
        PID=$(cat "$ENGINE_DIR/engine.pid")
        if kill -0 $PID 2>/dev/null; then
            echo -e "${YELLOW}Stopping engine (PID: $PID)...${NC}"
            kill $PID
        fi
        rm -f "$ENGINE_DIR/engine.pid"
    fi
    
    echo -e "${GREEN}All services stopped${NC}"
    exit 0
fi

# Start modules if not engine-only
if [ "$MODULES_ONLY" = true ] || [ "$ENGINE_ONLY" = false ]; then
    echo -e "${YELLOW}Starting Docker modules...${NC}"
    cd "$SCRIPT_DIR"
    
    if [ "$MINIMAL" = true ]; then
        # Start only essential services
        docker-compose -f docker-compose.dev.yml up -d \
            consul-server \
            seeder \
            consul-agent-echo echo-module \
            consul-agent-test test-module
    else
        # Start all services
        docker-compose -f docker-compose.dev.yml up -d
    fi
    
    # Wait for Consul
    wait_for_service "http://localhost:8500/v1/status/leader" "Consul"
    
    # Give modules time to register
    echo -e "${YELLOW}Waiting for modules to register...${NC}"
    sleep 10
    
    echo -e "${GREEN}Docker modules are running!${NC}"
    echo -e "Consul UI: http://localhost:8500"
fi

# Start engine if not modules-only
if [ "$ENGINE_ONLY" = true ] || [ "$MODULES_ONLY" = false ]; then
    # Check if engine port is available
    if ! check_port 39000; then
        echo -e "${RED}Port 39000 is already in use. Is engine already running?${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Starting engine in Quarkus dev mode...${NC}"
    cd "$ENGINE_DIR"
    
    # Create a dev-specific application properties
    cat > src/main/resources/application-dev-docker.yml << 'EOF'
# Dev mode configuration for Docker modules
quarkus:
  consul:
    host: localhost
    port: 8500
  consul-config:
    enabled: true
    properties-value-keys:
      - config/application
      - config/dev
      - config/${quarkus.profile}
  http:
    port: 39000
  grpc:
    server:
      port: 49000
  log:
    level: INFO
    category:
      "com.rokkon": DEBUG
      "io.quarkus.consul": DEBUG

rokkon:
  cluster:
    name: dev-cluster
  engine:
    name: dev-engine
EOF
    
    # Start Quarkus in dev mode
    echo -e "${GREEN}Starting Quarkus dev mode...${NC}"
    echo -e "${YELLOW}You can access the engine at:${NC}"
    echo -e "  REST API: http://localhost:39000"
    echo -e "  Dashboard: http://localhost:39000/index.html"
    echo -e "  Dev UI: http://localhost:39000/q/dev"
    echo ""
    echo -e "${YELLOW}Hot reload is enabled - make changes and they'll be applied automatically!${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
    echo ""
    
    # Run Quarkus dev mode
    ./gradlew quarkusDev \
        -Dquarkus.profile=dev-docker \
        -Dquarkus.consul.host=localhost \
        -Dquarkus.consul.port=8500
fi