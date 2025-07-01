#!/bin/bash

# Script to stop development environment

echo "🛑 Stopping Rokkon development environment..."

# Stop Consul if it exists (running or stopped)
if docker ps -a | grep -q rokkon-consul-dev; then
    echo "📦 Stopping Consul..."
    docker stop rokkon-consul-dev 2>/dev/null || true
    docker rm rokkon-consul-dev 2>/dev/null || true
    echo "✅ Consul stopped and removed"
else
    echo "ℹ️  Consul container not found"
fi

# Also stop any Consul server containers from earlier attempts
if docker ps -a | grep -q rokkon-consul-server; then
    echo "📦 Cleaning up old Consul server container..."
    docker stop rokkon-consul-server 2>/dev/null || true
    docker rm rokkon-consul-server 2>/dev/null || true
fi

echo "✅ Development environment stopped"