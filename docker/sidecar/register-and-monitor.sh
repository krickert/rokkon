#!/bin/bash
set -e

# Configuration from environment
MODULE_NAME=${MODULE_NAME:-"unknown"}
MODULE_HOST=${MODULE_HOST:-"localhost"}
MODULE_PORT=${MODULE_PORT:-"9090"}
ENGINE_HOST=${ENGINE_HOST:-"host.docker.internal"}
ENGINE_GRPC_PORT=${ENGINE_GRPC_PORT:-"48082"}
CONSUL_HOST=${CONSUL_HOST:-"host.docker.internal"}
CONSUL_PORT=${CONSUL_PORT:-"8500"}
RETRY_INTERVAL=${RETRY_INTERVAL:-"10"}
MAX_RETRIES=${MAX_RETRIES:-"30"}

echo "Starting sidecar for module: $MODULE_NAME"
echo "Module endpoint: $MODULE_HOST:$MODULE_PORT"
echo "Engine endpoint: $ENGINE_HOST:$ENGINE_GRPC_PORT"
echo "Consul endpoint: $CONSUL_HOST:$CONSUL_PORT"

# Function to check if module is healthy
check_module_health() {
    grpcurl -plaintext -connect-timeout 2 \
        $MODULE_HOST:$MODULE_PORT \
        grpc.health.v1.Health/Check >/dev/null 2>&1
}

# Function to register with Consul
register_with_consul() {
    echo "Registering service with Consul..."
    consul services register \
        -address=$MODULE_HOST \
        -port=$MODULE_PORT \
        -name=module-$MODULE_NAME \
        -tag=rokkon-module \
        -tag=cluster:${CLUSTER_NAME:-default} \
        -tag=grpc \
        -check="grpc $MODULE_HOST:$MODULE_PORT/grpc.health.v1.Health/Check" \
        -interval=10s
}

# Function to register with Engine
register_with_engine() {
    echo "Registering module with Engine..."
    java -jar /usr/local/bin/register-module.jar register \
        --module-host $MODULE_HOST \
        --module-port $MODULE_PORT \
        --engine-host $ENGINE_HOST \
        --engine-port $ENGINE_GRPC_PORT \
        --registration-host $MODULE_HOST \
        --registration-port $MODULE_PORT
}

# Wait for module to be ready
echo "Waiting for module to be ready..."
retry_count=0
while ! check_module_health; do
    if [ $retry_count -ge $MAX_RETRIES ]; then
        echo "Module failed to become healthy after $MAX_RETRIES attempts"
        exit 1
    fi
    echo "Module not ready yet, waiting $RETRY_INTERVAL seconds... (attempt $((retry_count + 1))/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
    retry_count=$((retry_count + 1))
done
echo "Module is healthy!"

# Register with Consul
if ! register_with_consul; then
    echo "Warning: Failed to register with Consul, continuing anyway..."
fi

# Register with Engine (retry with backoff)
engine_retry_count=0
while [ $engine_retry_count -lt $MAX_RETRIES ]; do
    if register_with_engine; then
        echo "Successfully registered with Engine!"
        break
    else
        echo "Failed to register with Engine, retrying in $RETRY_INTERVAL seconds... (attempt $((engine_retry_count + 1))/$MAX_RETRIES)"
        sleep $RETRY_INTERVAL
        engine_retry_count=$((engine_retry_count + 1))
    fi
done

if [ $engine_retry_count -ge $MAX_RETRIES ]; then
    echo "Warning: Failed to register with Engine after $MAX_RETRIES attempts"
fi

# Keep the sidecar running and monitor module health
echo "Sidecar running, monitoring module health..."
while true; do
    if ! check_module_health; then
        echo "Warning: Module health check failed at $(date)"
        # Could trigger re-registration or alerts here
    fi
    sleep 30
done