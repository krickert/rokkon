#!/bin/bash

echo "=== Cleaning up all test-module related containers and registrations ==="

# Function to clean up containers
cleanup_containers() {
    echo "Cleaning up Docker containers..."
    
    # Find all test-related containers (including the old naming)
    containers=$(docker ps -aq --filter "name=test" --filter "name!=testcontainers")
    
    if [ ! -z "$containers" ]; then
        echo "Found test-related containers:"
        docker ps -a --filter "name=test" --filter "name!=testcontainers" --format "table {{.Names}}\t{{.Status}}"
        
        echo "Stopping containers..."
        docker stop $containers 2>/dev/null || true
        
        echo "Removing containers..."
        docker rm -f $containers 2>/dev/null || true
    else
        echo "No test-related containers found"
    fi
}

# Function to check Consul registrations
check_consul_registrations() {
    echo ""
    echo "Checking Consul registrations..."
    
    if curl -s http://localhost:38500/v1/agent/services > /dev/null 2>&1; then
        echo "Consul services with 'test' in name:"
        curl -s http://localhost:38500/v1/agent/services | jq 'to_entries | .[] | select(.key | contains("test")) | {service: .key, details: .value}'
        
        # Deregister any test services
        services=$(curl -s http://localhost:38500/v1/agent/services | jq -r 'to_entries | .[] | select(.key | contains("test")) | .key')
        for service in $services; do
            echo "Deregistering service: $service"
            curl -X PUT "http://localhost:38500/v1/agent/service/deregister/$service"
        done
    else
        echo "Consul not accessible on port 38500"
    fi
}

# Function to check pipeline module registry
check_module_registry() {
    echo ""
    echo "Checking Pipeline Module Registry..."
    
    if curl -s http://localhost:39001/api/v1/module-discovery/dashboard > /dev/null 2>&1; then
        echo "Registered modules with 'test' in name:"
        curl -s http://localhost:39001/api/v1/module-discovery/dashboard | jq '.moduleServices[] | select(.moduleName | contains("test"))'
        
        # Call undeploy endpoint for test modules
        echo ""
        echo "Undeploying test-module via API..."
        curl -X DELETE http://localhost:39001/api/v1/module-management/test-module/undeploy
    else
        echo "Pipeline engine not accessible on port 39001"
    fi
}

# Execute cleanup
echo "Starting cleanup process..."
echo ""

# 1. Clean up via API first (if available)
check_module_registry

# 2. Clean up containers
cleanup_containers

# 3. Check Consul registrations
check_consul_registrations

echo ""
echo "=== Cleanup complete ==="
echo ""
echo "Verification - remaining containers:"
docker ps -a --filter "name=test" --filter "name!=testcontainers" --format "table {{.Names}}\t{{.Status}}"

echo ""
echo "To deploy a fresh test-module, use the UI or run:"
echo "curl -X POST http://localhost:39001/api/v1/module-management/test-module/deploy"