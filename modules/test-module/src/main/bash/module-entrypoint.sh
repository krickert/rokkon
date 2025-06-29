#!/bin/bash
set -e

# Configuration with defaults
MODULE_HOST=${MODULE_HOST:-0.0.0.0}
MODULE_PORT=${MODULE_PORT:-49095}
ENGINE_HOST=${ENGINE_HOST:-localhost}
ENGINE_PORT=${ENGINE_PORT:-8081}
HEALTH_CHECK=${HEALTH_CHECK:-true}
MAX_RETRIES=${MAX_RETRIES:-3}
STARTUP_TIMEOUT=${STARTUP_TIMEOUT:-60}
CHECK_INTERVAL=${CHECK_INTERVAL:-5}

# The address that should be registered with Consul for health checks
# This might be different from MODULE_HOST if running in Docker
EXTERNAL_MODULE_HOST=${EXTERNAL_MODULE_HOST:-${MODULE_HOST}}
EXTERNAL_MODULE_PORT=${EXTERNAL_MODULE_PORT:-${MODULE_PORT}}

# Function to register module with retries
register_module() {
  local retry_count=0
  local success=false

  while [ $retry_count -lt $MAX_RETRIES ] && [ "$success" = false ]; do
    echo "Registering module with engine (attempt $((retry_count+1))/${MAX_RETRIES})..."
    
    # Build CLI command with all options
    # CLI connects to module locally at localhost
    local cli_cmd="rokkon register --module-host=localhost --module-port=${MODULE_PORT} --engine-host=${ENGINE_HOST} --engine-port=${ENGINE_PORT}"
    
    # Add the external address that the engine should use to reach this module
    # This is what gets registered in Consul for health checks
    if [ -n "$EXTERNAL_MODULE_HOST" ]; then
      cli_cmd="$cli_cmd --registration-host=${EXTERNAL_MODULE_HOST}"
    fi
    
    if [ -n "$EXTERNAL_MODULE_PORT" ]; then
      cli_cmd="$cli_cmd --registration-port=${EXTERNAL_MODULE_PORT}"
    fi
    
    if [ "$HEALTH_CHECK" = false ]; then
      cli_cmd="$cli_cmd --skip-health-check"
    fi
    
    # Execute registration command with random HTTP port to avoid conflicts
    # Set a random port for the CLI's HTTP server to avoid conflicts
    export QUARKUS_HTTP_PORT=$((30000 + RANDOM % 10000))
    
    if $cli_cmd; then
      echo "Module registered successfully!"
      success=true
    else
      echo "Registration failed. Retrying in 5 seconds..."
      retry_count=$((retry_count+1))
      sleep 5
    fi
  done
  
  if [ "$success" = false ]; then
    echo "Failed to register module after ${MAX_RETRIES} attempts."
    return 1
  fi
  
  return 0
}

# Start the module in the background with port overrides
echo "Starting module..."
# Use QUARKUS_HTTP_PORT for HTTP and MODULE_PORT for gRPC services (with health checks)
HTTP_PORT=${QUARKUS_HTTP_PORT:-39095}
GRPC_PORT=${MODULE_PORT:-49095}
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -Dquarkus.http.port=${HTTP_PORT} -Dquarkus.grpc.server.port=${GRPC_PORT} -jar /deployments/quarkus-run.jar &
MODULE_PID=$!

# Give the module a moment to start up
sleep 5

# Register the module (CLI will handle health checks)
if register_module; then
  echo "Registration successful!"
else
  echo "Registration failed, but keeping module running for debugging..."
fi

# Keep the module running in foreground regardless of registration status
wait $MODULE_PID