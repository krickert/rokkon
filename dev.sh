#!/bin/bash
# Unified Pipeline Development Script with Consul Connect Service Mesh
# Supports both engine dev mode and module containers with CLI registration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONSUL_CONTAINER="pipeline-consul-dev"
CONSUL_PORT=8500
ENGINE_HTTP_PORT=38082
ENGINE_GRPC_PORT=48082
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Parse command line arguments
MODE="engine" # Default mode
MODULES=()

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --engine              Run engine in dev mode (default)"
    echo "  --with-modules        Run engine with module containers"
    echo "  --modules-only        Run only module containers (engine must be running)"
    echo "  --module NAME         Add a specific module (can be used multiple times)"
    echo "  --stop                Stop all containers"
    echo "  --help                Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Run engine in dev mode"
    echo "  $0 --with-modules                     # Run engine + all modules"
    echo "  $0 --module echo --module chunker     # Run engine + specific modules"
    echo "  $0 --modules-only                     # Run modules only (engine already running)"
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --engine)
            MODE="engine"
            shift
            ;;
        --with-modules)
            MODE="engine-with-modules"
            shift
            ;;
        --modules-only)
            MODE="modules-only"
            shift
            ;;
        --module)
            MODULES+=("$2")
            shift 2
            ;;
        --stop)
            MODE="stop"
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# If no specific modules requested, use default set
if [ ${#MODULES[@]} -eq 0 ] && [[ "$MODE" == *"modules"* ]]; then
    MODULES=("echo" "chunker" "parser" "embedder")
fi

echo -e "${GREEN}🚀 Pipeline Development Environment${NC}"
echo "=================================="
echo "Mode: $MODE"
if [ ${#MODULES[@]} -gt 0 ]; then
    echo "Modules: ${MODULES[*]}"
fi
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

# Function to stop all containers
stop_all() {
    echo -e "\n${YELLOW}Stopping all containers...${NC}"
    
    # Stop module containers
    for module in echo chunker parser embedder; do
        if docker ps -q -f name="pipeline-${module}" > /dev/null 2>&1; then
            echo "Stopping ${module} module..."
            docker stop "pipeline-${module}" > /dev/null 2>&1 || true
            docker rm "pipeline-${module}" > /dev/null 2>&1 || true
        fi
    done
    
    # Stop Consul
    if docker ps -q -f name=$CONSUL_CONTAINER > /dev/null 2>&1; then
        echo "Stopping Consul..."
        docker stop $CONSUL_CONTAINER > /dev/null 2>&1 || true
        docker rm $CONSUL_CONTAINER > /dev/null 2>&1 || true
    fi
    
    # Kill any gradle daemons
    if [ -f "$SCRIPT_DIR/gradlew" ]; then
        "$SCRIPT_DIR/gradlew" --stop > /dev/null 2>&1 || true
    fi
    
    echo -e "${GREEN}✅ All containers stopped${NC}"
}

# Function to start Consul
start_consul() {
    echo -e "\n1️⃣  Starting Consul with Connect enabled..."
    
    if docker ps -q -f name=$CONSUL_CONTAINER > /dev/null 2>&1; then
        if check_port $CONSUL_PORT; then
            echo -e "${YELLOW}⚠️  Consul is already running${NC}"
            return 0
        else
            docker stop $CONSUL_CONTAINER > /dev/null 2>&1 || true
            docker rm $CONSUL_CONTAINER > /dev/null 2>&1 || true
        fi
    fi
    
    # Create Consul config for Connect
    mkdir -p "$SCRIPT_DIR/consul-dev-config"
    cat > "$SCRIPT_DIR/consul-dev-config/consul.hcl" << 'EOF'
datacenter = "dc1"
data_dir = "/consul/data"
log_level = "INFO"
node_name = "consul-dev"
server = true
bootstrap_expect = 1

ui_config {
  enabled = true
}

connect {
  enabled = true
}

ports {
  grpc = 8502
}

client_addr = "0.0.0.0"
EOF
    
    echo "Starting Consul with Connect enabled..."
    docker run -d \
        --name $CONSUL_CONTAINER \
        --network host \
        -v "$SCRIPT_DIR/consul-dev-config:/consul/config" \
        -v "$SCRIPT_DIR/consul-data:/consul/data" \
        hashicorp/consul:latest \
        agent -dev -config-dir=/consul/config
    
    wait_for_service "Consul" $CONSUL_PORT || exit 1
    echo -e "${GREEN}✅ Consul started with Connect enabled${NC}"
    echo "   UI: http://localhost:$CONSUL_PORT/ui"
}

# Function to seed Consul configuration
seed_consul() {
    echo -e "\n2️⃣  Seeding Consul configuration..."
    cd "$SCRIPT_DIR/cli/seed-engine-consul-config"
    
    if [ ! -f "build/quarkus-app/quarkus-run.jar" ]; then
        echo "Building seed-engine-consul-config..."
        ./gradlew build -x test > /dev/null 2>&1
    fi
    
    # Check if already seeded
    if curl -s "http://localhost:$CONSUL_PORT/v1/kv/config/application" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Configuration already exists${NC}"
    else
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
    fi
}

# Function to register engine service in Consul
register_engine_service() {
    echo -e "\n3️⃣  Registering engine service..."
    
    # Create service definition with Connect proxy
    cat > /tmp/engine-service.json << EOF
{
  "service": {
    "name": "pipeline-engine",
    "tags": ["pipeline", "engine", "grpc", "connect"],
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
        "name": "Engine Health",
        "http": "http://localhost:$ENGINE_HTTP_PORT/q/health",
        "interval": "10s",
        "timeout": "5s"
      },
      {
        "name": "Engine gRPC",
        "grpc": "localhost:$ENGINE_GRPC_PORT",
        "grpc_use_tls": false,
        "interval": "10s"
      }
    ],
    "connect": {
      "sidecar_service": {
        "port": 21000,
        "proxy": {
          "upstreams": []
        }
      }
    }
  }
}
EOF
    
    curl -X PUT http://localhost:$CONSUL_PORT/v1/agent/service/register -d @/tmp/engine-service.json > /dev/null 2>&1
    echo -e "${GREEN}✅ Engine service registered${NC}"
}

# Function to build dependencies
build_dependencies() {
    echo -e "\n4️⃣  Building dependencies..."
    cd "$SCRIPT_DIR"
    
    echo "Building commons..."
    ./gradlew :commons:protobuf:build :commons:interface:build :commons:util:build :commons:data-util:build -x test > /dev/null 2>&1
    
    echo "Building engine modules..."
    ./gradlew :engine:consul:build :engine:validators:build -x test > /dev/null 2>&1
    
    # Build CLI for module registration
    echo "Building register-module CLI..."
    ./gradlew :cli:register-module:build -x test > /dev/null 2>&1
    
    echo -e "${GREEN}✅ Dependencies built${NC}"
}

# Function to start engine in dev mode
start_engine_dev() {
    echo -e "\n5️⃣  Starting Pipeline Engine in dev mode..."
    echo "=================================="
    echo -e "${YELLOW}Engine Configuration:${NC}"
    echo "  HTTP: localhost:$ENGINE_HTTP_PORT"
    echo "  gRPC: localhost:$ENGINE_GRPC_PORT"
    echo "  Consul: localhost:$CONSUL_PORT"
    echo ""
    echo -e "${GREEN}Features:${NC}"
    echo "  ✅ Live reload enabled"
    echo "  ✅ Consul Connect ready"
    echo "  ✅ Service discovery"
    echo "  ✅ Health checks"
    echo ""
    echo -e "${YELLOW}Endpoints:${NC}"
    echo "  Dashboard: http://localhost:$ENGINE_HTTP_PORT/"
    echo "  Health: http://localhost:$ENGINE_HTTP_PORT/q/health"
    echo "  API: http://localhost:$ENGINE_HTTP_PORT/q/swagger-ui"
    echo "  Consul: http://localhost:$CONSUL_PORT/ui"
    echo ""
    echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
    echo "=================================="
    
    cd "$SCRIPT_DIR"
    CONSUL_HOST=localhost \
    CONSUL_PORT=$CONSUL_PORT \
    ENGINE_HOST=localhost \
    QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
    QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
    ./gradlew :engine:pipestream:quarkusDev \
        -Dquarkus.http.port=$ENGINE_HTTP_PORT \
        -Dquarkus.grpc.server.port=$ENGINE_GRPC_PORT
}

# Function to deploy a module container
deploy_module() {
    local module_name=$1
    local module_image="pipeline/${module_name}-module:latest"
    local container_name="pipeline-${module_name}"
    
    # Port assignments based on module
    case $module_name in
        echo)
            HTTP_PORT=39091
            GRPC_PORT=49091
            ;;
        chunker)
            HTTP_PORT=39092
            GRPC_PORT=49092
            ;;
        parser)
            HTTP_PORT=39093
            GRPC_PORT=49093
            ;;
        embedder)
            HTTP_PORT=39094
            GRPC_PORT=49094
            ;;
        *)
            echo -e "${RED}Unknown module: $module_name${NC}"
            return 1
            ;;
    esac
    
    echo -e "\n${BLUE}Deploying $module_name module...${NC}"
    
    # Stop existing container if running
    if docker ps -q -f name="$container_name" > /dev/null 2>&1; then
        docker stop "$container_name" > /dev/null 2>&1 || true
        docker rm "$container_name" > /dev/null 2>&1 || true
    fi
    
    # Check if image exists, if not build it
    if ! docker image inspect "$module_image" > /dev/null 2>&1; then
        echo "Building $module_name module image..."
        cd "$SCRIPT_DIR/modules/$module_name"
        ./gradlew build -x test > /dev/null 2>&1
        docker build -f src/main/docker/Dockerfile.jvm -t "$module_image" . || {
            echo -e "${RED}Failed to build $module_name image${NC}"
            return 1
        }
    fi
    
    # Start module container
    docker run -d \
        --name "$container_name" \
        --network host \
        -e QUARKUS_HTTP_PORT=$HTTP_PORT \
        -e QUARKUS_GRPC_SERVER_PORT=$GRPC_PORT \
        -e CONSUL_HOST=localhost \
        -e CONSUL_PORT=$CONSUL_PORT \
        -e ENGINE_HOST=localhost \
        -e ENGINE_GRPC_PORT=$ENGINE_GRPC_PORT \
        "$module_image" || {
            echo -e "${RED}Failed to start $module_name container${NC}"
            return 1
        }
    
    # Wait for module to start
    wait_for_service "$module_name" $HTTP_PORT || return 1
    
    # Register module with engine using CLI
    echo "Registering $module_name with engine..."
    cd "$SCRIPT_DIR/cli/register-module"
    
    # Create module registration JSON
    cat > "/tmp/${module_name}-registration.json" << EOF
{
  "moduleId": "${module_name}-module",
  "moduleName": "${module_name^} Module",
  "moduleType": "${module_name^^}",
  "host": "localhost",
  "port": $GRPC_PORT,
  "httpPort": $HTTP_PORT,
  "capabilities": ["PROCESS", "GRPC"],
  "metadata": {
    "version": "1.0.0",
    "container": "$container_name"
  }
}
EOF
    
    # Register with engine
    java -jar build/quarkus-app/quarkus-run.jar \
        --engine-host localhost \
        --engine-port $ENGINE_GRPC_PORT \
        --module-config "/tmp/${module_name}-registration.json" \
        2>&1 | grep -E "(ERROR|WARN|INFO.*success|INFO.*registered)" || true
    
    echo -e "${GREEN}✅ $module_name module deployed and registered${NC}"
    echo "   HTTP: http://localhost:$HTTP_PORT"
    echo "   gRPC: localhost:$GRPC_PORT"
}

# Function to wait for engine to be ready
wait_for_engine() {
    echo -e "\n⏳ Waiting for engine to be ready..."
    local attempts=0
    while [ $attempts -lt 60 ]; do
        if curl -s "http://localhost:$ENGINE_HTTP_PORT/q/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Engine is ready${NC}"
            return 0
        fi
        sleep 1
        ((attempts++))
    done
    echo -e "${RED}❌ Engine failed to start${NC}"
    return 1
}

# Main execution based on mode
case $MODE in
    stop)
        stop_all
        exit 0
        ;;
    
    engine)
        # Just run engine in dev mode
        stop_all
        start_consul
        seed_consul
        register_engine_service
        build_dependencies
        start_engine_dev
        ;;
    
    engine-with-modules)
        # Run engine in background, then deploy modules
        stop_all
        start_consul
        seed_consul
        register_engine_service
        build_dependencies
        
        # Start engine in background
        echo -e "\n${YELLOW}Starting engine in background...${NC}"
        (
            cd "$SCRIPT_DIR"
            CONSUL_HOST=localhost \
            CONSUL_PORT=$CONSUL_PORT \
            ENGINE_HOST=localhost \
            QUARKUS_HTTP_PORT=$ENGINE_HTTP_PORT \
            QUARKUS_GRPC_SERVER_PORT=$ENGINE_GRPC_PORT \
            ./gradlew :engine:pipestream:quarkusDev \
                -Dquarkus.http.port=$ENGINE_HTTP_PORT \
                -Dquarkus.grpc.server.port=$ENGINE_GRPC_PORT \
                > engine.log 2>&1
        ) &
        ENGINE_PID=$!
        
        # Wait for engine to be ready
        wait_for_engine || {
            kill $ENGINE_PID 2>/dev/null || true
            exit 1
        }
        
        # Deploy modules
        for module in "${MODULES[@]}"; do
            deploy_module "$module"
        done
        
        echo -e "\n${GREEN}✅ All services running${NC}"
        echo "Engine log: tail -f engine.log"
        echo ""
        echo "Press Ctrl+C to stop all services"
        
        # Wait for interrupt
        trap "kill $ENGINE_PID 2>/dev/null || true; stop_all" INT
        wait $ENGINE_PID
        ;;
    
    modules-only)
        # Deploy modules to existing engine
        if ! check_port $ENGINE_HTTP_PORT; then
            echo -e "${RED}❌ Engine is not running on port $ENGINE_HTTP_PORT${NC}"
            echo "Start the engine first with: $0 --engine"
            exit 1
        fi
        
        for module in "${MODULES[@]}"; do
            deploy_module "$module"
        done
        
        echo -e "\n${GREEN}✅ Modules deployed${NC}"
        ;;
esac

# Cleanup on exit (for engine mode)
if [ "$MODE" = "engine" ]; then
    echo -e "\n${YELLOW}Shutting down...${NC}"
    curl -X PUT http://localhost:$CONSUL_PORT/v1/agent/service/deregister/pipeline-engine 2>/dev/null || true
    echo "Run '$0 --stop' to stop all containers"
fi