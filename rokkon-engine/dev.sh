#!/bin/bash

# Script to start development environment with Consul

echo "🚀 Starting Rokkon Engine development environment..."

# Check if Consul is already running
if nc -z localhost 8500 2>/dev/null; then
    echo "✅ Consul is already running on port 8500"
else
    echo "📦 Starting Consul in Docker..."
    docker run -d \
        --name rokkon-consul-dev \
        -p 8500:8500 \
        -p 8600:8600/udp \
        hashicorp/consul:latest agent -dev -ui -client=0.0.0.0

    # Wait for Consul to be ready
    echo "⏳ Waiting for Consul to start..."
    for i in {1..30}; do
        if nc -z localhost 8500 2>/dev/null; then
            echo "✅ Consul is ready!"
            break
        fi
        sleep 1
    done
fi

# Enable consul-config for dev mode
echo "🔧 Starting Quarkus in dev mode with Consul config enabled..."
./gradlew quarkusDev -Dquarkus.consul-config.enabled=true