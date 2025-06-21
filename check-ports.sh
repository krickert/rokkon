#!/bin/bash

echo "Checking for port conflicts in Rokkon Engine..."
echo "=============================================="

# Define all ports used by the project
declare -A PORTS=(
    ["MongoDB"]=27017
    ["Consul HTTP"]=8500
    ["Consul DNS"]=8600
    ["OpenSearch HTTP"]=9200
    ["OpenSearch Transport"]=9300
    ["Rokkon Engine HTTP"]=38090
    ["Rokkon Engine gRPC"]=49000
    ["Engine Consul HTTP"]=38091
    ["Engine Validators HTTP"]=38093
    ["Engine Registration HTTP"]=38094
    ["Engine Registration gRPC"]=49094
    ["Engine Models HTTP"]=38095
    ["Echo Module HTTP"]=39090
    ["Echo Module gRPC"]=49090
    ["Parser Module HTTP"]=39091
    ["Parser Module gRPC"]=49091
    ["Chunker Module HTTP"]=39092
    ["Chunker Module gRPC"]=49092
    ["Embedder Module HTTP"]=39093
    ["Embedder Module gRPC"]=49093
    ["Test Module HTTP"]=39095
    ["Test Module gRPC"]=49095
)

# Check each port
echo "Port Status:"
for service in "${!PORTS[@]}"; do
    port=${PORTS[$service]}
    if lsof -i :$port >/dev/null 2>&1 || netstat -tln 2>/dev/null | grep -q ":$port "; then
        echo "❌ $service (port $port) - IN USE"
    else
        echo "✅ $service (port $port) - available"
    fi
done

echo ""
echo "Debug Ports (JDWP):"
for i in {5005..5015}; do
    if lsof -i :$i >/dev/null 2>&1; then
        echo "❌ Debug port $i - IN USE"
    else
        echo "✅ Debug port $i - available"
    fi
done