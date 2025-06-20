#!/bin/bash

echo "=== Stopping Rokkon Development Environment ==="

# Stop the engine
if [ -f logs/rokkon-engine.pid ]; then
    echo "Stopping Rokkon Engine..."
    kill $(cat logs/rokkon-engine.pid) 2>/dev/null || true
    rm logs/rokkon-engine.pid
fi

# Stop all modules
./stop-all-modules.sh

# Stop Consul
echo "Stopping Consul..."
docker stop consul-dev 2>/dev/null || true
docker rm consul-dev 2>/dev/null || true

# Clean up any remaining processes (keeping this as a fallback)
pkill -f "consul agent" || true
pkill -f "gradlew quarkusDev" || true

echo ""
echo "All services stopped."
