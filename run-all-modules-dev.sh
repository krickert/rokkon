#!/bin/bash

# Script to run all modules in Quarkus dev mode
# Each module gets its own terminal/process

echo "Starting all Rokkon modules in dev mode..."

# Standardized ports - see PORT_MAPPING.md
# HTTP ports start with 3, gRPC ports start with 4

# Function to start a module
start_module() {
    local module_name=$1
    local grpc_port=$2
    local http_port=$3
    
    echo "Starting $module_name on gRPC port $grpc_port and HTTP port $http_port..."
    
    cd modules/$module_name
    
    # Export environment variables for the module
    export QUARKUS_GRPC_SERVER_PORT=$grpc_port
    export QUARKUS_HTTP_PORT=$http_port
    
    # Start in background
    nohup ./gradlew quarkusDev \
        -Dquarkus.grpc.server.port=$grpc_port \
        -Dquarkus.http.port=$http_port \
        > ../../logs/${module_name}-dev.log 2>&1 &
    
    echo "$!" > ../../logs/${module_name}.pid
    cd ../..
}

# Create logs directory
mkdir -p logs

# Kill any existing processes
if [ -f logs/*.pid ]; then
    echo "Stopping existing processes..."
    for pidfile in logs/*.pid; do
        if [ -f "$pidfile" ]; then
            pid=$(cat "$pidfile")
            kill $pid 2>/dev/null || true
            rm "$pidfile"
        fi
    done
fi

# Start each module with standardized ports (see PORT_MAPPING.md)
start_module "echo" 49090 38081
sleep 5
start_module "chunker" 49091 38082
sleep 5
start_module "parser" 49092 38083
sleep 5
start_module "embedder" 49093 38084
sleep 5
start_module "test-module" 49094 38085

echo ""
echo "All modules started! Logs are in the logs/ directory."
echo ""
echo "Module ports:"
echo "  echo:        gRPC=49090, HTTP=38081"
echo "  chunker:     gRPC=49091, HTTP=38082"
echo "  parser:      gRPC=49092, HTTP=38083"
echo "  embedder:    gRPC=49093, HTTP=38084"
echo "  test-module: gRPC=49094, HTTP=38085"
echo ""
echo "To stop all modules, run: ./stop-all-modules.sh"