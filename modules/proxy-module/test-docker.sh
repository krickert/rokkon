#!/bin/bash

# Configuration
MODULE_IMAGE=${MODULE_IMAGE:-rokkon/test-module:latest}
PROXY_IMAGE=${PROXY_IMAGE:-rokkon/proxy-module:latest}
NETWORK_NAME="rokkon-test-network"
MODULE_CONTAINER_NAME="test-module"
PROXY_CONTAINER_NAME="proxy-module"

# Create a Docker network if it doesn't exist
if ! docker network inspect $NETWORK_NAME >/dev/null 2>&1; then
    echo "Creating Docker network: $NETWORK_NAME"
    docker network create $NETWORK_NAME
fi

# Stop and remove existing containers if they exist
echo "Cleaning up existing containers..."
docker rm -f $MODULE_CONTAINER_NAME $PROXY_CONTAINER_NAME 2>/dev/null || true

# Start the test module container
echo "Starting test module container..."
docker run -d --name $MODULE_CONTAINER_NAME \
    --network $NETWORK_NAME \
    -p 9091:9090 \
    $MODULE_IMAGE

# Wait for the module to be ready
echo "Waiting for test module to be ready..."
for i in {1..10}; do
    if docker exec $MODULE_CONTAINER_NAME grpcurl -plaintext localhost:9090 list >/dev/null 2>&1; then
        echo "Test module is ready"
        break
    fi
    
    if [ $i -eq 10 ]; then
        echo "ERROR: Test module failed to start"
        docker logs $MODULE_CONTAINER_NAME
        exit 1
    fi
    
    echo "Waiting for test module to start... (attempt $i/10)"
    sleep 5
done

# Start the proxy module container
echo "Starting proxy module container..."
docker run -d --name $PROXY_CONTAINER_NAME \
    --network $NETWORK_NAME \
    -p 9090:9090 \
    -e MODULE_HOST=$MODULE_CONTAINER_NAME \
    -e MODULE_PORT=9090 \
    $PROXY_IMAGE

# Wait for the proxy to be ready
echo "Waiting for proxy module to be ready..."
for i in {1..10}; do
    if docker exec $PROXY_CONTAINER_NAME grpcurl -plaintext localhost:9090 list >/dev/null 2>&1; then
        echo "Proxy module is ready"
        break
    fi
    
    if [ $i -eq 10 ]; then
        echo "ERROR: Proxy module failed to start"
        docker logs $PROXY_CONTAINER_NAME
        exit 1
    fi
    
    echo "Waiting for proxy module to start... (attempt $i/10)"
    sleep 5
done

# Test the proxy by getting service registration
echo "Testing proxy by getting service registration..."
if docker exec $PROXY_CONTAINER_NAME grpcurl -plaintext localhost:9090 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration >/dev/null; then
    echo "SUCCESS: Proxy is working correctly"
    
    # Show the service registration details
    echo "Service registration details:"
    docker exec $PROXY_CONTAINER_NAME grpcurl -plaintext localhost:9090 com.rokkon.search.sdk.PipeStepProcessor/GetServiceRegistration | grep -E 'module_name|version|proxy_enabled'
else
    echo "ERROR: Failed to get service registration from proxy"
    docker logs $PROXY_CONTAINER_NAME
    exit 1
fi

echo "Test completed successfully"
echo "Proxy container: $PROXY_CONTAINER_NAME"
echo "Module container: $MODULE_CONTAINER_NAME"
echo "To clean up, run: docker rm -f $PROXY_CONTAINER_NAME $MODULE_CONTAINER_NAME"