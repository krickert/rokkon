#!/bin/bash

echo "Testing orphaned module redeployment..."

# Check if service is running
if ! curl -s http://localhost:39001/q/health > /dev/null 2>&1; then
    echo "Error: Engine service is not running on port 39001"
    echo "Please start the engine with: ./gradlew :engine:pipestream:quarkusDev"
    exit 1
fi

# Get orphaned modules
echo "Fetching orphaned modules..."
orphaned=$(curl -s http://localhost:39001/api/v1/module-management/orphaned)

if [ -z "$orphaned" ] || [ "$orphaned" = "[]" ]; then
    echo "No orphaned modules found"
    echo ""
    echo "To test, first deploy a module, then deregister it:"
    echo "  1. Deploy a module from the UI"
    echo "  2. Deregister it from the UI (but leave containers running)"
    echo "  3. Run this script again"
    exit 0
fi

echo "Orphaned modules:"
echo "$orphaned" | jq .

# Get the first orphaned module's container ID (handle snake_case)
container_id=$(echo "$orphaned" | jq -r '.[0].container_id // .[0].containerId')
module_name=$(echo "$orphaned" | jq -r '.[0].module_name // .[0].moduleName')

if [ -z "$container_id" ] || [ "$container_id" = "null" ]; then
    echo "Error: Could not extract container ID from orphaned modules"
    exit 1
fi

echo ""
echo "Attempting to redeploy module: $module_name (container: $container_id)"

# Attempt redeployment
response=$(curl -s -X POST http://localhost:39001/api/v1/module-management/orphaned/$container_id/redeploy)

echo "Redeployment response: $response"

# Check if it worked by waiting a bit and checking registered modules
echo ""
echo "Waiting 20 seconds for redeployment to complete..."
sleep 20

echo ""
echo "Checking registered modules..."
curl -s http://localhost:39001/api/v1/module-discovery/dashboard | jq '.moduleServices[] | select(.moduleName == "'$module_name'")'

# Also check for the registrar container
echo ""
echo "Checking for registrar containers..."
docker ps --filter "label=pipeline.orphan.registrar=true" --format "table {{.Names}}\t{{.Status}}\t{{.Labels}}"