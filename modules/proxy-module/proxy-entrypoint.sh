#!/bin/bash

# Configuration
MODULE_HOST=${MODULE_HOST:-localhost}
MODULE_PORT=${MODULE_PORT:-9091}
PROXY_PORT=${PROXY_PORT:-9090}
MAX_RETRIES=${MAX_RETRIES:-5}
STARTUP_TIMEOUT=${STARTUP_TIMEOUT:-60}
CHECK_INTERVAL=${CHECK_INTERVAL:-5}

echo "Starting Quarkus Proxy Service for PipeStepProcessor Modules"
echo "============================================================"
echo "Proxy configuration:"
echo "  - Module host: $MODULE_HOST"
echo "  - Module port: $MODULE_PORT"
echo "  - Proxy port: $PROXY_PORT"

# Start the actual module in the background
if [ -f "/deployments/module-entrypoint.sh" ]; then
    echo "Starting backend module using module-entrypoint.sh..."
    # Set environment variables for the module
    export MODULE_PORT=$MODULE_PORT
    
    # Start the module in the background
    /deployments/module-entrypoint.sh &
    MODULE_PID=$!
    
    # Wait for module to be ready
    echo "Waiting for module to start (timeout: ${STARTUP_TIMEOUT}s)..."
    ELAPSED=0
    while [ $ELAPSED -lt $STARTUP_TIMEOUT ]; do
        if grpcurl -plaintext $MODULE_HOST:$MODULE_PORT list > /dev/null 2>&1; then
            echo "Module started successfully on $MODULE_HOST:$MODULE_PORT"
            break
        fi
        
        # Check if the module process is still running
        if ! kill -0 $MODULE_PID > /dev/null 2>&1; then
            echo "ERROR: Module process exited unexpectedly"
            exit 1
        fi
        
        echo "Waiting for module to start... ($ELAPSED/${STARTUP_TIMEOUT}s)"
        sleep $CHECK_INTERVAL
        ELAPSED=$((ELAPSED + CHECK_INTERVAL))
    done
    
    if [ $ELAPSED -ge $STARTUP_TIMEOUT ]; then
        echo "ERROR: Timed out waiting for module to start"
        exit 1
    fi
else
    echo "WARNING: No module-entrypoint.sh found. Assuming module is started externally."
    
    # Wait for module to be available
    echo "Checking if module is available at $MODULE_HOST:$MODULE_PORT..."
    RETRIES=0
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if grpcurl -plaintext $MODULE_HOST:$MODULE_PORT list > /dev/null 2>&1; then
            echo "Module is available at $MODULE_HOST:$MODULE_PORT"
            break
        fi
        
        RETRIES=$((RETRIES + 1))
        if [ $RETRIES -ge $MAX_RETRIES ]; then
            echo "ERROR: Could not connect to module at $MODULE_HOST:$MODULE_PORT after $MAX_RETRIES attempts"
            exit 1
        fi
        
        echo "Waiting for module to be available... (attempt $RETRIES/$MAX_RETRIES)"
        sleep $CHECK_INTERVAL
    done
fi

# Start the Quarkus proxy
echo "Starting Quarkus proxy on port $PROXY_PORT..."
exec java \
    -Dmodule.host=$MODULE_HOST \
    -Dmodule.port=$MODULE_PORT \
    -Dquarkus.grpc.server.port=$PROXY_PORT \
    -jar /deployments/quarkus-run.jar