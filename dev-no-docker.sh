#!/bin/bash
# Development script to run engine with Consul (no Docker)
# This script:
# 1. Starts Consul server in dev mode
# 2. Starts Consul sidecar agent
# 3. Seeds configuration
# 4. Runs the engine JAR directly

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
CONSUL_AGENT_PORT="${CONSUL_AGENT_PORT:-8501}"
ENGINE_HTTP_PORT="${ENGINE_HTTP_PORT:-38082}"
ENGINE_GRPC_PORT="${ENGINE_GRPC_PORT:-48082}"
INSTANCE_NAME="${INSTANCE_NAME:-default}"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CONSUL_DIR="$SCRIPT_DIR/.consul-${INSTANCE_NAME}"
CONSUL_SERVER_DATA="$CONSUL_DIR/server-data"
CONSUL_AGENT_DATA="$CONSUL_DIR/agent-data"

# Derived ports (add offsets for multiple instances)
CONSUL_SERVER_GRPC_PORT=$((CONSUL_SERVER_PORT + 2))  # 8502
CONSUL_SERVER_SERF_LAN_PORT=$((CONSUL_SERVER_PORT - 199))  # 8301
CONSUL_SERVER_SERF_WAN_PORT=$((CONSUL_SERVER_PORT - 198))  # 8302
CONSUL_SERVER_SERVER_PORT=$((CONSUL_SERVER_PORT - 200))  # 8300

CONSUL_AGENT_GRPC_PORT=$((CONSUL_AGENT_PORT + 3))  # 8504
CONSUL_AGENT_DNS_PORT=$((CONSUL_AGENT_PORT + 100))  # 8601
CONSUL_AGENT_SERF_LAN_PORT=$((CONSUL_AGENT_PORT + 10))  # 8511
CONSUL_AGENT_SERVER_PORT=$((CONSUL_AGENT_PORT + 9))  # 8510

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
    
    # Kill Consul processes
    pkill -f "consul agent.*dev" 2>/dev/null || true
    pkill -f "consul agent.*data-dir=$CONSUL_AGENT_DATA" 2>/dev/null || true
    
    # Kill engine process
    pkill -f "quarkus-run.jar" 2>/dev/null || true
    
    # Wait a moment for processes to die
    sleep 2
    
    echo -e "${GREEN}✅ All services stopped${NC}"
}

# Function to start Consul server
start_consul_server() {
    echo -e "\n${BLUE}1️⃣  Starting Consul server in dev mode...${NC}"
    
    if check_port $CONSUL_SERVER_PORT; then
        echo -e "${YELLOW}⚠️  Consul server already running on port $CONSUL_SERVER_PORT${NC}"
        return 0
    fi
    
    # Create data directory
    mkdir -p "$CONSUL_SERVER_DATA"
    
    # Start Consul server in dev mode
    "$CONSUL_DIR/consul" agent -dev \
        -ui \
        -client=0.0.0.0 \
        -bind=127.0.0.1 \
        -data-dir="$CONSUL_SERVER_DATA" \
        -log-level=info \
        > "$CONSUL_DIR/consul-server.log" 2>&1 &
    
    CONSUL_SERVER_PID=$!
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

# Function to start Consul sidecar agent
start_consul_sidecar() {
    echo -e "\n${BLUE}2️⃣  Starting Consul sidecar agent...${NC}"
    
    if check_port $CONSUL_AGENT_PORT; then
        echo -e "${YELLOW}⚠️  Consul agent already running on port $CONSUL_AGENT_PORT${NC}"
        return 0
    fi
    
    # Create data directory and config
    mkdir -p "$CONSUL_AGENT_DATA"
    
    # Create agent configuration
    cat > "$CONSUL_DIR/agent-config.json" << EOF
{
  "datacenter": "dc1",
  "data_dir": "$CONSUL_AGENT_DATA",
  "log_level": "INFO",
  "node_name": "engine-sidecar-$INSTANCE_NAME",
  "server": false,
  "ui": false,
  "bind_addr": "127.0.0.1",
  "advertise_addr": "127.0.0.1",
  "retry_join": ["127.0.0.1:$CONSUL_SERVER_PORT"],
  "ports": {
    "http": $CONSUL_AGENT_PORT,
    "grpc": $CONSUL_AGENT_GRPC_PORT,
    "dns": $CONSUL_AGENT_DNS_PORT,
    "serf_lan": $CONSUL_AGENT_SERF_LAN_PORT,
    "serf_wan": -1,
    "server": $CONSUL_AGENT_SERVER_PORT
  },
  "connect": {
    "enabled": true
  },
  "services": [{
    "name": "pipeline-engine",
    "port": $ENGINE_GRPC_PORT,
    "address": "127.0.0.1",
    "tags": ["engine", "grpc"],
    "meta": {
      "service-type": "ENGINE",
      "grpc-port": "$ENGINE_GRPC_PORT",
      "http-port": "$ENGINE_HTTP_PORT"
    },
    "checks": [{
      "name": "Engine Health",
      "http": "http://localhost:$ENGINE_HTTP_PORT/q/health",
      "interval": "10s",
      "timeout": "5s"
    }]
  }]
}
EOF
    
    # Start sidecar agent
    "$CONSUL_DIR/consul" agent \
        -config-file="$CONSUL_DIR/agent-config.json" \
        > "$CONSUL_DIR/consul-agent.log" 2>&1 &
    
    CONSUL_AGENT_PID=$!
    echo "Consul agent PID: $CONSUL_AGENT_PID"
    
    # Wait for agent to be ready
    echo -n "Waiting for Consul agent..."
    for i in {1..30}; do
        if check_port $CONSUL_AGENT_PORT; then
            echo -e " ${GREEN}OK${NC}"
            echo -e "${GREEN}✅ Consul sidecar agent started${NC}"
            echo "   HTTP: http://localhost:$CONSUL_AGENT_PORT"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    
    echo -e " ${RED}FAILED${NC}"
    echo "Check logs at: $CONSUL_DIR/consul-agent.log"
    return 1
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
    
    # Check if already seeded
    if curl -s "http://localhost:$CONSUL_SERVER_PORT/v1/kv/config/application" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Configuration already exists${NC}"
    else
        echo "Seeding configuration..."
        java -jar build/quarkus-app/quarkus-run.jar \
            -h localhost \
            -p $CONSUL_SERVER_PORT \
            --key config/application \
            --import seed-data.json \
            --force
        
        # Also seed dev profile
        java -jar build/quarkus-app/quarkus-run.jar \
            -h localhost \
            -p $CONSUL_SERVER_PORT \
            --key config/dev \
            --import seed-data.json \
            --force
        
        echo -e "${GREEN}✅ Configuration seeded${NC}"
    fi
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
    echo "  Consul Server: localhost:$CONSUL_SERVER_PORT"
    echo "  Consul Agent: localhost:$CONSUL_AGENT_PORT (sidecar)"
    echo ""
    echo -e "${GREEN}Services:${NC}"
    echo "  ✅ Consul Server (dev mode)"
    echo "  ✅ Consul Sidecar Agent"
    echo "  ✅ Configuration seeded"
    echo ""
    echo -e "${YELLOW}Endpoints:${NC}"
    echo "  Dashboard: http://localhost:$ENGINE_HTTP_PORT/"
    echo "  Health: http://localhost:$ENGINE_HTTP_PORT/q/health"
    echo "  API: http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
    echo "  Consul UI: http://localhost:$CONSUL_SERVER_PORT/ui"
    echo ""
    echo -e "${YELLOW}Logs:${NC}"
    echo "  Consul Server: $CONSUL_DIR/consul-server.log"
    echo "  Consul Agent: $CONSUL_DIR/consul-agent.log"
    echo ""
    echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
    echo "=================================="
    
    cd "$SCRIPT_DIR"
    
    # Run engine (connect to sidecar agent on localhost)
    QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
    QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
    QUARKUS_CONSUL_HOST=localhost \
    QUARKUS_CONSUL_PORT=$CONSUL_AGENT_PORT \
    QUARKUS_CONSUL_CONFIG_ENABLED=true \
    QUARKUS_CONSUL_CONFIG_AGENT_HOST_PORT=localhost:$CONSUL_SERVER_PORT \
    QUARKUS_PROFILE=dev \
    CLUSTER_NAME=test-cluster \
    PIPELINE_CLUSTER_NAME=test-cluster \
    PIPELINE_ENGINE_NAME=test-engine \
    PIPELINE_ENGINE_HOST=127.0.0.1 \
    java -jar engine/pipestream/build/quarkus-app/quarkus-run.jar
}

# Main execution
main() {
    # Parse command line arguments
    case "${1:-}" in
        stop)
            stop_all
            exit 0
            ;;
        help|--help|-h)
            echo "Usage: $0 [stop]"
            echo ""
            echo "  (no args)  Start all services"
            echo "  stop       Stop all services"
            echo ""
            echo "Environment variables (for multiple instances):"
            echo "  INSTANCE_NAME       Instance name (default: 'default')"
            echo "  CONSUL_SERVER_PORT  Consul server port (default: 8500)"
            echo "  CONSUL_AGENT_PORT   Consul agent port (default: 8501)"  
            echo "  ENGINE_HTTP_PORT    Engine HTTP port (default: 38082)"
            echo "  ENGINE_GRPC_PORT    Engine gRPC port (default: 48082)"
            echo ""
            echo "Example - Run a second instance:"
            echo "  INSTANCE_NAME=instance2 CONSUL_SERVER_PORT=8600 CONSUL_AGENT_PORT=8601 \\"
            echo "  ENGINE_HTTP_PORT=38083 ENGINE_GRPC_PORT=48083 $0"
            exit 0
            ;;
    esac
    
    # Trap Ctrl+C to stop all services
    trap stop_all INT
    
    # Download Consul if needed
    download_consul
    
    # Stop any existing services
    stop_all
    
    # Start services
    start_consul_server || exit 1
    start_consul_sidecar || exit 1
    seed_consul
    build_engine || exit 1
    run_engine
}

# Run main function
main "$@"