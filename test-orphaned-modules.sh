#!/bin/bash

echo "Testing orphaned modules API..."

# Check if service is running
if ! curl -s http://localhost:39001/q/health > /dev/null 2>&1; then
    echo "Error: Engine service is not running on port 39001"
    echo "Please start the engine with: ./gradlew :engine:pipestream:quarkusDev"
    exit 1
fi

echo "Engine is running. Fetching orphaned modules..."

# Get orphaned modules
response=$(curl -s http://localhost:39001/api/v1/module-management/orphaned)

if [ -z "$response" ]; then
    echo "No response from API"
else
    echo "Orphaned modules response:"
    echo "$response" | jq . 2>/dev/null || echo "$response"
fi

echo ""
echo "Current Docker containers with pipeline labels:"
docker ps --filter "label=pipeline.module" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Labels}}"