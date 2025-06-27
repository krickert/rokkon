#!/bin/bash

# Script to start development environment with Consul (interactive mode)

echo "🚀 Starting Rokkon Engine development environment..."

# Check if Consul is already running
if docker ps | grep -q rokkon-consul-dev; then
    echo "✅ Consul is already running"
else
    # Check if container exists but is stopped
    if docker ps -a | grep -q rokkon-consul-dev; then
        echo "🔄 Starting existing Consul container..."
        docker start rokkon-consul-dev
    else
        echo "📦 Starting new Consul container..."
        docker run -d \
            --name rokkon-consul-dev \
            -p 8500:8500 \
            -p 8600:8600/udp \
            --add-host=host.docker.internal:host-gateway \
            hashicorp/consul:1.21.2 agent -dev -ui -client=0.0.0.0
    fi

    # Wait for Consul to be ready
    echo "⏳ Waiting for Consul to start..."
    for i in {1..30}; do
        if nc -z localhost 8500 2>/dev/null; then
            echo "✅ Consul is ready!"
            echo "📊 Consul UI available at: http://localhost:8500/ui"
            break
        fi
        sleep 1
    done
fi

# Run quarkusDev in foreground so we can see the output
echo "🔧 Starting Quarkus in dev mode with Consul config enabled..."
echo "💡 Setting ENGINE_HOST=host.docker.internal for Docker connectivity"
echo "Press Ctrl+C to stop"
ENGINE_HOST=host.docker.internal ./gradlew quarkusDev -Dquarkus.consul-config.enabled=true