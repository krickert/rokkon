#!/bin/bash
# Simplified development script to run engine with Consul (no Docker, no sidecar)
# This script:
# 1. Starts Consul server in dev mode
# 2. Seeds configuration
# 3. Runs the engine JAR directly

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration with environment variable overrides
CONSUL_VERSION="${CONSUL_VERSION:-1.17.1}"
CONSUL_SERVER_PORT="${CONSUL_SERVER_PORT:-8500}"
ENGINE_HTTP_PORT="${ENGINE_HTTP_PORT:-38082}"
ENGINE_GRPC_PORT="${ENGINE_GRPC_PORT:-48082}"
INSTANCE_NAME="${INSTANCE_NAME:-default}"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CONSUL_DIR="$SCRIPT_DIR/.consul-${INSTANCE_NAME}"
CONSUL_SERVER_DATA="$CONSUL_DIR/server-data"

# Derived ports
CONSUL_SERVER_GRPC_PORT=$((CONSUL_SERVER_PORT + 2))  # 8502
CONSUL_SERVER_SERF_LAN_PORT=$((CONSUL_SERVER_PORT - 199))  # 8301
CONSUL_SERVER_SERF_WAN_PORT=$((CONSUL_SERVER_PORT - 198))  # 8302
CONSUL_SERVER_SERVER_PORT=$((CONSUL_SERVER_PORT - 200))  # 8300
CONSUL_SERVER_DNS_PORT=$((CONSUL_SERVER_PORT + 100))  # 8600

# Function to check if a port is open
check_port() {
    nc -z localhost $1 2>/dev/null
}

# Function to download Consul if needed
download_consul() {
    if [ ! -f "$CONSUL_DIR/consul" ]; then
        echo -e "${YELLOW}Downloading Consul ${CONSUL_VERSION}...${NC}"
        mkdir -p "$CONSUL_DIR"
        cd "$CONSUL_DIR"
        
        # Detect OS and architecture
        OS=$(uname -s | tr '[:upper:]' '[:lower:]')
        ARCH=$(uname -m)
        if [ "$ARCH" = "x86_64" ]; then
            ARCH="amd64"
        fi
        
        # Download Consul
        CONSUL_URL="https://releases.hashicorp.com/consul/${CONSUL_VERSION}/consul_${CONSUL_VERSION}_${OS}_${ARCH}.zip"
        curl -L -o consul.zip "$CONSUL_URL"
        unzip -o consul.zip
        rm consul.zip
        chmod +x consul
        cd "$SCRIPT_DIR"
        echo -e "${GREEN}✅ Consul downloaded${NC}"
    fi
}

# Function to stop all services
stop_all() {
    echo -e "\n${YELLOW}Stopping all services...${NC}"
    
    # Kill Consul processes for this instance
    if [ -f "$CONSUL_DIR/consul-server.pid" ]; then
        kill $(cat "$CONSUL_DIR/consul-server.pid") 2>/dev/null || true
        rm -f "$CONSUL_DIR/consul-server.pid"
    fi
    
    # Kill engine process for this instance
    if [ -f "$CONSUL_DIR/engine.pid" ]; then
        kill $(cat "$CONSUL_DIR/engine.pid") 2>/dev/null || true
        rm -f "$CONSUL_DIR/engine.pid"
    fi
    
    # Wait a moment for processes to die
    sleep 2
    
    # Clean up Consul data directory
    if [ -d "$CONSUL_SERVER_DATA" ]; then
        echo "Cleaning up Consul data directory..."
        rm -rf "$CONSUL_SERVER_DATA"
    fi
    
    echo -e "${GREEN}✅ All services stopped and data cleaned${NC}"
}

# Function to start Consul server
start_consul_server() {
    echo -e "\n${BLUE}1️⃣  Starting Consul server in dev mode...${NC}"
    
    if check_port $CONSUL_SERVER_PORT; then
        echo -e "${YELLOW}⚠️  Port $CONSUL_SERVER_PORT already in use${NC}"
        return 1
    fi
    
    # Create data directory
    mkdir -p "$CONSUL_SERVER_DATA"
    
    # Start Consul server in dev mode with custom ports
    "$CONSUL_DIR/consul" agent -dev \
        -ui \
        -client=0.0.0.0 \
        -bind=127.0.0.1 \
        -data-dir="$CONSUL_SERVER_DATA" \
        -log-level=info \
        -http-port=$CONSUL_SERVER_PORT \
        -grpc-port=$CONSUL_SERVER_GRPC_PORT \
        -serf-lan-port=$CONSUL_SERVER_SERF_LAN_PORT \
        -serf-wan-port=$CONSUL_SERVER_SERF_WAN_PORT \
        -server-port=$CONSUL_SERVER_SERVER_PORT \
        -dns-port=$CONSUL_SERVER_DNS_PORT \
        > "$CONSUL_DIR/consul-server.log" 2>&1 &
    
    CONSUL_SERVER_PID=$!
    echo $CONSUL_SERVER_PID > "$CONSUL_DIR/consul-server.pid"
    echo "Consul server PID: $CONSUL_SERVER_PID"
    
    # Wait for Consul to be ready
    echo -n "Waiting for Consul server..."
    for i in {1..30}; do
        if check_port $CONSUL_SERVER_PORT; then
            echo -e " ${GREEN}OK${NC}"
            echo -e "${GREEN}✅ Consul server started${NC}"
            echo "   UI: http://localhost:$CONSUL_SERVER_PORT/ui"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    
    echo -e " ${RED}FAILED${NC}"
    echo "Check logs at: $CONSUL_DIR/consul-server.log"
    return 1
}

# Function to clean up old registrations
cleanup_consul_registrations() {
    echo -e "\n${BLUE}2️⃣  Cleaning up old service registrations...${NC}"
    
    # Get all service instances
    local services=$(curl -s "http://localhost:$CONSUL_SERVER_PORT/v1/agent/services" | jq -r 'to_entries[] | select(.value.Service == "pipeline-engine") | .key')
    
    if [ -n "$services" ]; then
        echo "Found old registrations to clean up:"
        for service_id in $services; do
            echo "  - Deregistering: $service_id"
            curl -s -X PUT "http://localhost:$CONSUL_SERVER_PORT/v1/agent/service/deregister/$service_id"
        done
        echo -e "${GREEN}✅ Old registrations cleaned up${NC}"
    else
        echo "No old registrations found"
    fi
}

# Function to seed Consul configuration
seed_consul() {
    echo -e "\n${BLUE}3️⃣  Seeding Consul configuration...${NC}"
    
    cd "$SCRIPT_DIR/cli/seed-engine-consul-config"
    
    # Build seeder if needed
    if [ ! -f "build/quarkus-app/quarkus-run.jar" ]; then
        echo "Building seed-engine-consul-config..."
        ./gradlew build -x test > /dev/null 2>&1
    fi
    
    # Always seed configuration with --force
    echo "Seeding configuration..."
    java -jar build/quarkus-app/quarkus-run.jar \
        -h localhost \
        -p $CONSUL_SERVER_PORT \
        --key config/application \
        --import seed-data.json \
        --force
    
    # Also seed integration-local-no-docker profile
    java -jar build/quarkus-app/quarkus-run.jar \
        -h localhost \
        -p $CONSUL_SERVER_PORT \
        --key config/integration-local-no-docker \
        --import seed-data.json \
        --force
    
    echo -e "${GREEN}✅ Configuration seeded at multiple locations${NC}"
}

# Function to build engine
build_engine() {
    echo -e "\n${BLUE}4️⃣  Building engine...${NC}"
    
    cd "$SCRIPT_DIR"
    
    # First build common modules
    echo "Building common modules..."
    ./gradlew :commons:protobuf:build :commons:interface:build :commons:util:build :commons:data-util:build -x test
    
    # Then build engine dependencies
    echo "Building engine dependencies..."
    ./gradlew :engine:consul:build :engine:validators:build :engine:dynamic-grpc:build -x test
    
    # Finally build engine
    echo "Building engine..."
    ./gradlew :engine:pipestream:build -x test
    
    if [ ! -f "engine/pipestream/build/quarkus-app/quarkus-run.jar" ]; then
        echo -e "${RED}❌ Engine build failed${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✅ Engine built${NC}"
}

# Function to run engine
run_engine() {
    echo -e "\n${BLUE}5️⃣  Starting Pipeline Engine...${NC}"
    echo "=================================="
    echo -e "${YELLOW}Engine Configuration:${NC}"
    echo "  HTTP: localhost:$ENGINE_HTTP_PORT"
    echo "  gRPC: localhost:$ENGINE_GRPC_PORT"
    echo "  Consul: localhost:$CONSUL_SERVER_PORT"
    echo ""
    echo -e "${GREEN}Services:${NC}"
    echo "  ✅ Consul Server (dev mode)"
    echo "  ✅ Configuration seeded"
    echo ""
    echo -e "${BLUE}🌐 Web Interfaces:${NC}"
    echo "  Dashboard:         http://localhost:$ENGINE_HTTP_PORT/"
    echo "  Swagger UI:        http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
    echo "  Consul UI:         http://localhost:$CONSUL_SERVER_PORT/ui"
    echo ""
    echo -e "${BLUE}📡 API Endpoints:${NC}"
    echo "  Health Check:      http://localhost:$ENGINE_HTTP_PORT/q/health"
    echo "  Metrics:           http://localhost:$ENGINE_HTTP_PORT/q/metrics"
    echo "  Engine Info:       http://localhost:$ENGINE_HTTP_PORT/api/v1/engine"
    echo "  Engine Config:     http://localhost:$ENGINE_HTTP_PORT/api/v1/engine/config"
    echo "  Modules:           http://localhost:$ENGINE_HTTP_PORT/api/v1/modules"
    echo "  Consul Status:     http://localhost:$ENGINE_HTTP_PORT/api/v1/consul/status"
    echo ""
    echo -e "${BLUE}🔧 Pipeline Management:${NC}"
    echo "  List Pipelines:    http://localhost:$ENGINE_HTTP_PORT/api/v1/clusters/test-cluster/pipelines"
    echo "  Deploy Pipeline:   POST http://localhost:$ENGINE_HTTP_PORT/api/v1/clusters/test-cluster/pipelines/{pipelineId}"
    echo ""
    echo -e "${YELLOW}📋 Logs:${NC}"
    echo "  Consul Server: $CONSUL_DIR/consul-server.log"
    echo "  Engine: Console output below"
    echo ""
    echo -e "${YELLOW}⚡ Quick Test Commands:${NC}"
    echo "  curl http://localhost:$ENGINE_HTTP_PORT/api/v1/engine | jq"
    echo "  curl http://localhost:$ENGINE_HTTP_PORT/api/v1/modules | jq"
    echo ""
    echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
    echo "=================================="
    
    cd "$SCRIPT_DIR"
    
    # Run engine (connect directly to Consul server)
    # Override the engine.host property from application-dev.yml
    QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
    QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
    QUARKUS_CONSUL_HOST=localhost \
    QUARKUS_CONSUL_PORT=$CONSUL_SERVER_PORT \
    QUARKUS_CONSUL_CONFIG_ENABLED=true \
    QUARKUS_CONSUL_CONFIG_AGENT_HOST_PORT=localhost:$CONSUL_SERVER_PORT \
    QUARKUS_PROFILE=integration-local-no-docker \
    CLUSTER_NAME=test-cluster \
    PIPELINE_CLUSTER_NAME=test-cluster \
    PIPELINE_ENGINE_NAME=test-engine-$INSTANCE_NAME \
    ENGINE_HOST=127.0.0.1 \
    java -Dengine.host=127.0.0.1 -jar engine/pipestream/build/quarkus-app/quarkus-run.jar &
    
    ENGINE_PID=$!
    echo $ENGINE_PID > "$CONSUL_DIR/engine.pid"
}

# Main execution
main() {
    # Parse command line arguments
    case "${1:-}" in
        stop)
            stop_all
            exit 0
            ;;
        --delete-consul-data)
            echo -e "${YELLOW}Deleting all Consul data directories...${NC}"
            rm -rf "$SCRIPT_DIR"/.consul-*/
            echo -e "${GREEN}✅ All Consul data deleted${NC}"
            exit 0
            ;;
        help|--help|-h)
            echo "Usage: $0 [stop|--delete-consul-data]"
            echo ""
            echo "  (no args)           Start all services"
            echo "  stop                Stop all services"
            echo "  --delete-consul-data  Delete all Consul data directories"
            echo ""
            echo "Environment variables (for multiple instances):"
            echo "  INSTANCE_NAME       Instance name (default: 'default')"
            echo "  CONSUL_SERVER_PORT  Consul server port (default: 8500)"
            echo "  ENGINE_HTTP_PORT    Engine HTTP port (default: 38082)"
            echo "  ENGINE_GRPC_PORT    Engine gRPC port (default: 48082)"
            echo ""
            echo "Example - Run a second instance:"
            echo "  INSTANCE_NAME=instance2 CONSUL_SERVER_PORT=8600 \\"
            echo "  ENGINE_HTTP_PORT=38083 ENGINE_GRPC_PORT=48083 $0"
            exit 0
            ;;
    esac
    
    # Trap Ctrl+C to stop all services
    trap stop_all INT
    
    # Download Consul if needed
    download_consul
    
    # Stop any existing services for this instance
    stop_all
    
    # Start services
    start_consul_server || exit 1
    cleanup_consul_registrations
    seed_consul
    build_engine || exit 1
    run_engine
    
    # Wait for engine process
    wait $ENGINE_PID
}

# Run main function
main "$@"