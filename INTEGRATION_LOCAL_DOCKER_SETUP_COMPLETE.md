# Complete Integration Setup Guide: From Local to Docker with Consul Sidecars

## Overview

This guide documents the complete journey from running the Rokkon Pipeline Engine locally to a full Docker Compose setup with Consul sidecars. It includes all scripts, configurations, and lessons learned along the way.

## Table of Contents
1. [Architecture Evolution](#architecture-evolution)
2. [Local Development Scripts](#local-development-scripts)
   - [Simple Direct Connection (`dev-no-docker-simple.sh`)](#simple-direct-connection)
   - [With Sidecar Pattern (`dev-no-docker-with-sidecar.sh`)](#with-sidecar-pattern)
3. [Docker Compose Setup](#docker-compose-setup)
4. [Key Learnings](#key-learnings)

---

## Architecture Evolution

### 1. Direct Connection (Simplest)
```
Engine ──────► Consul Server
```

### 2. Sidecar Pattern (Production-like)
```
Engine ──────► Consul Agent (Sidecar) ──────► Consul Server
```

### 3. Docker with Sidecars (Full Production Pattern)
```
┌─────────────────────────┐     ┌─────────────────────────┐
│   Engine Container      │     │   Module Container      │
│   network_mode:         │     │   network_mode:         │
│   "service:agent1"      │     │   "service:agent2"      │
└───────────┬─────────────┘     └───────────┬─────────────┘
            │                               │
┌───────────▼─────────────┐     ┌───────────▼─────────────┐
│   Consul Agent 1        │     │   Consul Agent 2        │
│   (Engine Sidecar)      │     │   (Module Sidecar)      │
└───────────┬─────────────┘     └───────────┬─────────────┘
            │                               │
            └───────────┬───────────────────┘
                        │
            ┌───────────▼─────────────┐
            │   Consul Server         │
            │   (Service Discovery)   │
            └─────────────────────────┘
```

---

## Local Development Scripts

### Simple Direct Connection

#### Script: `dev-no-docker-simple.sh`

This script demonstrates the simplest setup where the engine connects directly to Consul.

```bash
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
CONSUL_VERSION="${CONSUL_VERSION:-1.21}"
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
    
    # Build process...
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
    
    cd "$SCRIPT_DIR"
    
    # Run engine (connect directly to Consul server)
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
    seed_consul
    build_engine || exit 1
    run_engine
    
    # Show available endpoints
    echo "=================================="
    echo -e "${BLUE}🌐 Web Interfaces:${NC}"
    echo "  Dashboard:         http://localhost:$ENGINE_HTTP_PORT/"
    echo "  Swagger UI:        http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
    echo "  Consul UI:         http://localhost:$CONSUL_SERVER_PORT/ui"
    echo "=================================="
    
    # Wait for engine process
    wait $ENGINE_PID
}

# Run main function
main "$@"
```

#### Key Features:
- Downloads Consul automatically if not present
- Manages PIDs for clean shutdown
- Seeds configuration at multiple keys
- Supports multiple instances via environment variables
- Direct connection: Engine → Consul Server

---

### With Sidecar Pattern

#### Script: `dev-no-docker-with-sidecar.sh`

This script implements the sidecar pattern where the engine connects to a local Consul agent instead of directly to the server.

```bash
#!/bin/bash
# Development script to run engine with Consul server and sidecar agent (no Docker)
# This script:
# 1. Starts Consul server in dev mode
# 2. Starts Consul agent (sidecar) that joins the server
# 3. Seeds configuration through the agent
# 4. Runs the engine JAR connecting to the agent (not server)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration with environment variable overrides
CONSUL_VERSION="${CONSUL_VERSION:-1.21}"
CONSUL_SERVER_PORT="${CONSUL_SERVER_PORT:-8500}"
CONSUL_AGENT_PORT="${CONSUL_AGENT_PORT:-8501}"  # Agent runs on different port
ENGINE_HTTP_PORT="${ENGINE_HTTP_PORT:-38082}"
ENGINE_GRPC_PORT="${ENGINE_GRPC_PORT:-48082}"
INSTANCE_NAME="${INSTANCE_NAME:-default}"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CONSUL_DIR="$SCRIPT_DIR/.consul-${INSTANCE_NAME}"
CONSUL_SERVER_DATA="$CONSUL_DIR/server-data"
CONSUL_AGENT_DATA="$CONSUL_DIR/agent-data"

# Derived ports for server
CONSUL_SERVER_GRPC_PORT=$((CONSUL_SERVER_PORT + 2))  # 8502
CONSUL_SERVER_SERF_LAN_PORT=$((CONSUL_SERVER_PORT - 199))  # 8301
CONSUL_SERVER_SERF_WAN_PORT=$((CONSUL_SERVER_PORT - 198))  # 8302
CONSUL_SERVER_SERVER_PORT=$((CONSUL_SERVER_PORT - 200))  # 8300
CONSUL_SERVER_DNS_PORT=$((CONSUL_SERVER_PORT + 100))  # 8600

# Derived ports for agent (sidecar)
CONSUL_AGENT_GRPC_PORT=$((CONSUL_AGENT_PORT + 3))  # 8504 (avoid conflict with server)
CONSUL_AGENT_SERF_LAN_PORT=$((CONSUL_AGENT_PORT + 800))  # 9301 (different from server)
CONSUL_AGENT_DNS_PORT=$((CONSUL_AGENT_PORT + 100))  # 8601

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
    
    if [ -f "$CONSUL_DIR/consul-agent.pid" ]; then
        kill $(cat "$CONSUL_DIR/consul-agent.pid") 2>/dev/null || true
        rm -f "$CONSUL_DIR/consul-agent.pid"
    fi
    
    # Kill engine process for this instance
    if [ -f "$CONSUL_DIR/engine.pid" ]; then
        kill $(cat "$CONSUL_DIR/engine.pid") 2>/dev/null || true
        rm -f "$CONSUL_DIR/engine.pid"
    fi
    
    # Wait a moment for processes to die
    sleep 2
    
    # Clean up Consul data directories
    if [ -d "$CONSUL_SERVER_DATA" ]; then
        echo "Cleaning up Consul server data..."
        rm -rf "$CONSUL_SERVER_DATA"
    fi
    
    if [ -d "$CONSUL_AGENT_DATA" ]; then
        echo "Cleaning up Consul agent data..."
        rm -rf "$CONSUL_AGENT_DATA"
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

# Function to start Consul agent (sidecar)
start_consul_agent() {
    echo -e "\n${BLUE}2️⃣  Starting Consul agent (sidecar)...${NC}"
    
    if check_port $CONSUL_AGENT_PORT; then
        echo -e "${YELLOW}⚠️  Port $CONSUL_AGENT_PORT already in use${NC}"
        return 1
    fi
    
    # Create data directory
    mkdir -p "$CONSUL_AGENT_DATA"
    
    # Start Consul agent in client mode
    "$CONSUL_DIR/consul" agent \
        -data-dir="$CONSUL_AGENT_DATA" \
        -node="engine-sidecar-$INSTANCE_NAME" \
        -client=0.0.0.0 \
        -bind=127.0.0.1 \
        -retry-join=127.0.0.1:$CONSUL_SERVER_SERF_LAN_PORT \
        -log-level=info \
        -http-port=$CONSUL_AGENT_PORT \
        -grpc-port=$CONSUL_AGENT_GRPC_PORT \
        -serf-lan-port=$CONSUL_AGENT_SERF_LAN_PORT \
        -dns-port=$CONSUL_AGENT_DNS_PORT \
        > "$CONSUL_DIR/consul-agent.log" 2>&1 &
    
    CONSUL_AGENT_PID=$!
    echo $CONSUL_AGENT_PID > "$CONSUL_DIR/consul-agent.pid"
    echo "Consul agent PID: $CONSUL_AGENT_PID"
    
    # Wait for agent to be ready and join cluster
    echo -n "Waiting for Consul agent to join cluster..."
    for i in {1..30}; do
        if check_port $CONSUL_AGENT_PORT; then
            # Check if agent has joined the cluster
            MEMBERS=$(curl -s "http://localhost:$CONSUL_AGENT_PORT/v1/agent/members" | jq -r '.[].Status' | grep -c "1" || echo "0")
            if [ "$MEMBERS" -ge "2" ]; then
                echo -e " ${GREEN}OK${NC}"
                echo -e "${GREEN}✅ Consul agent started and joined cluster${NC}"
                echo "   Agent API: http://localhost:$CONSUL_AGENT_PORT"
                
                # Show cluster members
                echo -e "\n${BLUE}Cluster Members:${NC}"
                curl -s "http://localhost:$CONSUL_AGENT_PORT/v1/agent/members" | jq -r '.[] | "\(.Name) - \(.Tags.role // "client")"'
                return 0
            fi
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
    echo -e "\n${BLUE}4️⃣  Seeding Consul configuration (via agent)...${NC}"
    
    cd "$SCRIPT_DIR/cli/seed-engine-consul-config"
    
    # Build seeder if needed
    if [ ! -f "build/quarkus-app/quarkus-run.jar" ]; then
        echo "Building seed-engine-consul-config..."
        ./gradlew build -x test > /dev/null 2>&1
    fi
    
    # Seed configuration through the AGENT (not server)
    echo "Seeding configuration through agent..."
    java -jar build/quarkus-app/quarkus-run.jar \
        -h localhost \
        -p $CONSUL_AGENT_PORT \
        --key config/application \
        --import seed-data.json \
        --force
    
    # Also seed integration-local-no-docker profile
    java -jar build/quarkus-app/quarkus-run.jar \
        -h localhost \
        -p $CONSUL_AGENT_PORT \
        --key config/integration-local-no-docker \
        --import seed-data.json \
        --force
    
    echo -e "${GREEN}✅ Configuration seeded via agent${NC}"
    
    # Verify data is replicated
    echo -e "\n${BLUE}Verifying data replication...${NC}"
    SERVER_KEYS=$(curl -s "http://localhost:$CONSUL_SERVER_PORT/v1/kv/?keys" | jq -r '.[]' | wc -l)
    AGENT_KEYS=$(curl -s "http://localhost:$CONSUL_AGENT_PORT/v1/kv/?keys" | jq -r '.[]' | wc -l)
    echo "Keys visible from server: $SERVER_KEYS"
    echo "Keys visible from agent: $AGENT_KEYS"
}

# Function to build engine
build_engine() {
    echo -e "\n${BLUE}5️⃣  Building engine...${NC}"
    
    cd "$SCRIPT_DIR"
    
    # Build process...
    ./gradlew :engine:pipestream:build -x test
    
    if [ ! -f "engine/pipestream/build/quarkus-app/quarkus-run.jar" ]; then
        echo -e "${RED}❌ Engine build failed${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✅ Engine built${NC}"
}

# Function to run engine
run_engine() {
    echo -e "\n${BLUE}6️⃣  Starting Pipeline Engine (connecting to agent)...${NC}"
    
    cd "$SCRIPT_DIR"
    
    # Run engine (connect to AGENT, not server)
    QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
    QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
    QUARKUS_CONSUL_HOST=localhost \
    QUARKUS_CONSUL_PORT=$CONSUL_AGENT_PORT \
    QUARKUS_CONSUL_CONFIG_ENABLED=true \
    QUARKUS_CONSUL_CONFIG_AGENT_HOST_PORT=localhost:$CONSUL_AGENT_PORT \
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
            echo "  (no args)           Start all services with sidecar pattern"
            echo "  stop                Stop all services"
            echo "  --delete-consul-data  Delete all Consul data directories"
            echo ""
            echo "This script demonstrates the sidecar pattern:"
            echo "  - Consul server runs centrally"
            echo "  - Consul agent (sidecar) runs alongside the engine"
            echo "  - Engine connects to the agent, not directly to server"
            echo "  - Agent handles service registration and health checks"
            exit 0
            ;;
    esac
    
    # Trap Ctrl+C to stop all services
    trap stop_all INT
    
    # Download Consul if needed
    download_consul
    
    # Stop any existing services for this instance
    stop_all
    
    # Start services in order
    start_consul_server || exit 1
    start_consul_agent || exit 1
    seed_consul
    build_engine || exit 1
    run_engine
    
    # Show available endpoints
    echo "=================================="
    echo -e "${YELLOW}Engine Configuration:${NC}"
    echo "  HTTP: localhost:$ENGINE_HTTP_PORT"
    echo "  gRPC: localhost:$ENGINE_GRPC_PORT"
    echo "  Consul Agent: localhost:$CONSUL_AGENT_PORT"
    echo ""
    echo -e "${GREEN}Services:${NC}"
    echo "  ✅ Consul Server (dev mode)"
    echo "  ✅ Consul Agent (sidecar)"
    echo "  ✅ Configuration seeded"
    echo ""
    echo -e "${BLUE}🌐 Web Interfaces:${NC}"
    echo "  Dashboard:         http://localhost:$ENGINE_HTTP_PORT/"
    echo "  Swagger UI:        http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
    echo "  Consul UI:         http://localhost:$CONSUL_SERVER_PORT/ui"
    echo "=================================="
    
    # Wait for engine process
    wait $ENGINE_PID
}

# Run main function
main "$@"
```

#### Key Differences from Simple Script:
- Runs TWO Consul processes: server + agent
- Agent gets unique node name: `engine-sidecar-$INSTANCE_NAME`
- Seeds configuration through agent (port 8501)
- Engine connects to agent, not server
- Agent uses different ports to avoid conflicts
- Shows cluster membership after agent joins

---

## Docker Compose Setup

### 📁 Required Files Structure

```
.
├── docker-compose.yml
├── .env
├── docker-compose-helper.sh
├── cli/
│   └── seed-engine-consul-config/
│       ├── seed-data.json
│       └── Dockerfile (if not exists, will be created)
└── modules/
    └── echo/
        └── (module code)
```

### 1. `.env` File

```env
# .env - Environment configuration for Docker Compose

# --- Consul Configuration ---
CONSUL_VERSION=1.21

# --- Image Names (UPDATE THESE with your actual images) ---
ENGINE_IMAGE=rokkon/rokkon-engine:latest
ECHO_MODULE_IMAGE=rokkon/echo-module:latest

# --- Configuration Profile ---
# This determines the Consul key path: config/${PROFILE}
PROFILE=docker-compose

# --- Engine Ports (external:internal) ---
ENGINE_REST_PORT=38082
ENGINE_GRPC_PORT=48082

# --- Echo Module Ports ---
ECHO_MODULE_REST_PORT=39092
ECHO_MODULE_GRPC_PORT=49092

# --- Cluster Configuration ---
CLUSTER_NAME=docker-cluster
ENGINE_NAME=engine-docker
```

### 2. `docker-compose.yml`

```yaml
# docker-compose.yml
version: '3.8'

services:
  # ==========================================
  # 1. CONSUL SERVER - Central Service Discovery
  # ==========================================
  consul-server:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-server
    ports:
      - "8500:8500"      # HTTP API & UI
      - "8600:8600/tcp"  # DNS
      - "8600:8600/udp"  # DNS
    volumes:
      - consul_data:/consul/data
    command: >
      agent -server -ui 
      -client=0.0.0.0 
      -bind=0.0.0.0
      -bootstrap-expect=1 
      -dev
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    healthcheck:
      test: ["CMD", "consul", "info"]
      interval: 5s
      timeout: 3s
      retries: 10
    networks:
      - rokkon-network

  # ==========================================
  # 2. CONFIGURATION SEEDER - One-time Job
  # ==========================================
  seeder:
    build:
      context: ./cli/seed-engine-consul-config
      dockerfile: Dockerfile
    container_name: consul-seeder
    depends_on:
      consul-server:
        condition: service_healthy
    command:
      - "java"
      - "-jar"
      - "build/quarkus-app/quarkus-run.jar"
      - "-h"
      - "consul-server"
      - "-p"
      - "8500"
      - "--key"
      - "config/${PROFILE:-docker-compose}"
      - "--import"
      - "seed-data.json"
      - "--force"
    volumes:
      - ./cli/seed-engine-consul-config/seed-data.json:/app/seed-data.json:ro
    networks:
      - rokkon-network

  # ==========================================
  # 3. ENGINE SIDECAR - Consul Agent for Engine
  # ==========================================
  consul-agent-engine:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-engine
    depends_on:
      consul-server:
        condition: service_healthy
    command: >
      agent 
      -node="engine-sidecar"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - rokkon-network

  # ==========================================
  # 3a. ENGINE APPLICATION
  # ==========================================
  engine:
    image: ${ENGINE_IMAGE}
    container_name: engine-app
    depends_on:
      seeder:
        condition: service_completed_successfully
      consul-agent-engine:
        condition: service_started
    ports:
      - "${ENGINE_REST_PORT:-38082}:38082"
      - "${ENGINE_GRPC_PORT:-48082}:48082"
    environment:
      # Only set what the profile can't know
      - QUARKUS_PROFILE=${PROFILE:-docker-compose}
      # Optional overrides for cluster configuration
      - CLUSTER_NAME=${CLUSTER_NAME:-docker-cluster}
      - PIPELINE_CLUSTER_NAME=${CLUSTER_NAME:-docker-cluster}
      - PIPELINE_ENGINE_NAME=${ENGINE_NAME:-engine-docker}
    network_mode: "service:consul-agent-engine"  # Share network with sidecar
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:38082/q/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ==========================================
  # 4. ECHO MODULE SIDECAR - Consul Agent for Echo
  # ==========================================
  consul-agent-echo:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-echo
    depends_on:
      consul-server:
        condition: service_healthy
    command: >
      agent 
      -node="echo-sidecar"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - rokkon-network

  # ==========================================
  # 4a. ECHO MODULE APPLICATION
  # ==========================================
  echo-module:
    image: ${ECHO_MODULE_IMAGE}
    container_name: echo-module-app
    depends_on:
      engine:
        condition: service_healthy
      consul-agent-echo:
        condition: service_started
    ports:
      - "${ECHO_MODULE_REST_PORT:-39092}:39092"
      - "${ECHO_MODULE_GRPC_PORT:-49092}:49092"
    environment:
      # Module Configuration
      - MODULE_NAME=echo
      - MODULE_HTTP_PORT=39092
      - MODULE_GRPC_PORT=49092
      # Let module discover engine via Consul service discovery
      # The module will query Consul for the "pipeline-engine" service
      - QUARKUS_PROFILE=${PROFILE:-docker-compose}
    network_mode: "service:consul-agent-echo"  # Share network with sidecar

# ==========================================
# NETWORKS & VOLUMES
# ==========================================
networks:
  rokkon-network:
    driver: bridge

volumes:
  consul_data:
```

### 3. `docker-compose-helper.sh`

```bash
#!/bin/bash
# Helper script for Docker Compose operations

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Functions
show_status() {
    echo -e "\n${BLUE}🐳 Docker Compose Status:${NC}"
    docker-compose ps
}

show_endpoints() {
    echo -e "\n${BLUE}🌐 Available Endpoints:${NC}"
    echo "  Consul UI:         http://localhost:8500/ui"
    echo "  Engine Dashboard:  http://localhost:38082/"
    echo "  Engine Health:     http://localhost:38082/q/health"
    echo "  Engine Swagger:    http://localhost:38082/q/swagger-ui"
    echo "  Echo Module:       http://localhost:39092/"
    echo ""
    echo -e "${BLUE}📡 Internal Network (rokkon-network):${NC}"
    echo "  Consul Server:     consul-server:8500"
    echo "  Engine (via sidecar): consul-agent-engine:38082/48082"
    echo "  Echo (via sidecar):   consul-agent-echo:39092/49092"
}

start_services() {
    echo -e "${BLUE}Starting Docker Compose environment...${NC}"
    docker-compose up -d
    
    echo -e "${YELLOW}Waiting for services to be ready...${NC}"
    sleep 10
    
    # Check if engine is healthy
    echo -n "Checking engine health..."
    for i in {1..30}; do
        if curl -f http://localhost:38082/q/health >/dev/null 2>&1; then
            echo -e " ${GREEN}OK${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    
    show_status
    show_endpoints
}

stop_services() {
    echo -e "${YELLOW}Stopping Docker Compose environment...${NC}"
    docker-compose down
    echo -e "${GREEN}✅ Services stopped${NC}"
}

clean_all() {
    echo -e "${RED}Cleaning up everything (including volumes)...${NC}"
    docker-compose down -v
    echo -e "${GREEN}✅ All cleaned up${NC}"
}

view_logs() {
    SERVICE=${1:-}
    if [ -z "$SERVICE" ]; then
        docker-compose logs -f
    else
        docker-compose logs -f "$SERVICE"
    fi
}

# Main execution
case "${1:-}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        stop_services
        start_services
        ;;
    status)
        show_status
        show_endpoints
        ;;
    clean)
        clean_all
        ;;
    logs)
        view_logs "${2:-}"
        ;;
    help|--help|-h)
        echo "Usage: $0 [start|stop|restart|status|clean|logs [service]]"
        echo ""
        echo "Commands:"
        echo "  start    - Start all services"
        echo "  stop     - Stop all services"
        echo "  restart  - Restart all services"
        echo "  status   - Show service status and endpoints"
        echo "  clean    - Stop services and remove volumes"
        echo "  logs     - View logs (optionally specify service)"
        echo ""
        echo "Examples:"
        echo "  $0 start"
        echo "  $0 logs engine"
        echo "  $0 logs consul-agent-engine"
        ;;
    *)
        start_services
        ;;
esac
```

### 4. Seeder Dockerfile (if needed)

If your seeder doesn't have a Dockerfile, create one at `cli/seed-engine-consul-config/Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the built application
COPY build/quarkus-app/ ./build/quarkus-app/

# The command is specified in docker-compose.yml
ENTRYPOINT []
```

---

## Key Learnings

### 1. Port Conflicts Resolution
- **Problem**: Consul agents and servers using same ports
- **Solution**: 
  - Server gRPC: 8502, Agent gRPC: 8504
  - Server Serf LAN: 8301, Agent Serf LAN: 9301

### 2. Node Name Conflicts
- **Problem**: Multiple Consul instances with same node name
- **Solution**: Unique node names like `engine-sidecar-${INSTANCE_NAME}`

### 3. Network Mode Magic
- **Problem**: Containers need to share network for sidecar pattern
- **Solution**: `network_mode: "service:consul-agent-engine"`
- **Result**: Application sees agent as `localhost:8500`

### 4. Startup Dependencies
- **Key Pattern**:
  1. Consul server (with health check)
  2. Seeder (wait for completion)
  3. Consul agents
  4. Applications

### 5. Configuration Seeding
- **Best Practice**: Seed through agent, not server
- **Multiple Keys**: Seed both `config/application` and profile-specific keys

### 6. Service Discovery
- **Local**: Use `localhost` or `127.0.0.1`
- **Docker**: Use container names (e.g., `consul-server`, `engine`)
- **With Sidecars**: Applications use `localhost`, sidecars use container names

### 7. Debugging Tips
- Check logs: `docker-compose logs [service-name]`
- Verify Consul membership: `curl http://localhost:8500/v1/agent/members`
- Test connectivity: `docker exec [container] ping [other-container]`
- Inspect network: `docker network inspect [network-name]`

## Best Practices Applied

### 1. Docker Compose Command Syntax
- **Wrong**: Using pipe (`|`) for multi-line commands
- **Right**: Use YAML list format for arguments:
  ```yaml
  command:
    - "java"
    - "-jar"
    - "app.jar"
    - "--arg1"
    - "value1"
  ```

### 2. Environment Variable Simplification
- **Principle**: Let Quarkus profiles handle configuration
- **Practice**: Only set variables the profile can't know:
  - Profile name itself (`QUARKUS_PROFILE`)
  - Optional overrides (cluster names, etc.)
- **Benefit**: Single source of truth, cleaner compose file

### 3. Service Discovery Over Hard-Coding
- **Anti-pattern**: Passing direct URLs like `ENGINE_URL=http://engine:38082`
- **Best Practice**: Let modules discover services via Consul
- **Implementation**: Module queries Consul for "pipeline-engine" service
- **Benefit**: Decoupled, dynamic, scalable

### 4. Version Pinning
- **Development**: Can use `latest` for flexibility
- **Production**: Always pin versions (e.g., `hashicorp/consul:1.21`)
- **Benefit**: Reproducible builds, predictable behavior

### 5. Health Checks for Reliability
- **Pattern**: Add health checks to critical services
- **Implementation**:
  ```yaml
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:38082/q/health"]
    interval: 10s
    timeout: 5s
    retries: 5
  ```
- **Usage**: `depends_on: { service: { condition: service_healthy } }`

---

## Next Steps

1. **Build Docker Images**: Ensure all components have proper Docker images
2. **Update `.env`**: Set correct image names and ports
3. **Test Locally First**: Use the bash scripts to verify everything works
4. **Deploy with Docker**: Use `docker-compose up -d`
5. **Add More Modules**: Copy the sidecar pattern for each new module

This setup provides a solid foundation for running Rokkon with proper service mesh patterns, preparing for production deployment with full Consul Connect features.