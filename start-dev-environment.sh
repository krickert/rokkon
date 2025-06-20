#!/bin/bash

# Complete dev environment startup script
set -e

echo "=== Starting Rokkon Development Environment ==="

# Function to check if a service is running
check_service() {
    local service=$1
    local port=$2
    nc -z localhost $port > /dev/null 2>&1
}

# 1. Start Consul if not already running
echo ""
echo "1. Checking Consul..."
if ! check_service "Consul" 8500; then
    echo "   Starting Consul in dev mode using Docker..."
    docker run -d \
      --name consul-dev \
      -p 8500:8500 \
      -p 8600:8600/udp \
      consul:latest agent -dev -ui -client=0.0.0.0 > logs/consul.log 2>&1

    # Wait for Consul to be ready
    echo -n "   Waiting for Consul to start"
    for i in {1..30}; do
        if check_service "Consul" 8500; then
            echo " ✓"
            break
        fi
        echo -n "."
        sleep 1
    done

    if ! check_service "Consul" 8500; then
        echo " ✗"
        echo "   ERROR: Consul failed to start. Check logs/consul.log"
        exit 1
    fi
else
    echo "   Consul is already running ✓"
fi

# 2. Start all modules
echo ""
echo "2. Starting all modules..."
./run-all-modules-dev.sh

# 3. Wait for modules to be ready
echo ""
echo "3. Waiting for modules to be ready..."
sleep 10

# 4. Check module health
echo ""
echo "4. Checking module health..."
for module in echo chunker parser embedder test-module; do
    port=$((8080 + $(echo "echo=1 chunker=2 parser=3 embedder=4 test-module=5" | grep -o "$module=[0-9]" | cut -d= -f2)))
    if check_service "$module" $port; then
        echo "   $module: ✓ (HTTP port $port)"
    else
        echo "   $module: ✗ (HTTP port $port)"
    fi
done

# 5. Start the engine
echo ""
echo "5. Starting Rokkon Engine..."
cd rokkon-engine
nohup ./gradlew quarkusDev \
    -Dquarkus.http.port=8080 \
    -Dquarkus.grpc.server.port=9000 \
    > ../logs/rokkon-engine-dev.log 2>&1 &
echo $! > ../logs/rokkon-engine.pid
cd ..

# 6. Wait for engine to be ready
echo -n "   Waiting for engine to start"
for i in {1..30}; do
    if check_service "Engine" 8080; then
        echo " ✓"
        break
    fi
    echo -n "."
    sleep 1
done

# 7. Summary
echo ""
echo "=== Development Environment Status ==="
echo ""
echo "Services:"
echo "  Consul UI:      http://localhost:8500"
echo "  Rokkon Engine:  http://localhost:8080"
echo ""
echo "Modules (gRPC/HTTP):"
echo "  echo:        localhost:9090 / localhost:8081"
echo "  chunker:     localhost:9091 / localhost:8082"
echo "  parser:      localhost:9092 / localhost:8083"
echo "  embedder:    localhost:9093 / localhost:8084"
echo "  test-module: localhost:9094 / localhost:8085"
echo ""
echo "Logs are available in the logs/ directory"
echo ""
echo "To stop everything, run: ./stop-dev-environment.sh"
