#!/bin/bash
set -e

# Configuration with defaults
MODULE_NAME=${MODULE_NAME:-parser}
MODULE_HOST=${MODULE_HOST:-0.0.0.0}
MODULE_PORT=${MODULE_PORT:-9090}  # Legacy single port
MODULE_HTTP_PORT=${MODULE_HTTP_PORT:-${MODULE_PORT}}
MODULE_GRPC_PORT=${MODULE_GRPC_PORT:-${MODULE_PORT}}
ENGINE_HOST=${ENGINE_HOST:-localhost}
ENGINE_PORT=${ENGINE_PORT:-8081}
CONSUL_HOST=${CONSUL_HOST:-""}
CONSUL_PORT=${CONSUL_PORT:-"-1"}
HEALTH_CHECK=${HEALTH_CHECK:-true}
MAX_RETRIES=${MAX_RETRIES:-30}
STARTUP_TIMEOUT=${STARTUP_TIMEOUT:-60}
CHECK_INTERVAL=${CHECK_INTERVAL:-5}

# Function to register module with retries
register_module() {
  local retry_count=0
  local success=false

  while [ $retry_count -lt $MAX_RETRIES ] && [ "$success" = false ]; do
    echo "Registering module with engine (attempt $((retry_count+1))/${MAX_RETRIES})..."
    
    # Build CLI command with gRPC port for registration
    local cli_cmd="pipeline register --module-host=${MODULE_HOST} --module-port=${MODULE_GRPC_PORT} --engine-host=${ENGINE_HOST} --engine-port=${ENGINE_PORT}"
    
    # Add optional parameters if provided
    if [ -n "$CONSUL_HOST" ]; then
      cli_cmd="$cli_cmd --consul-host=${CONSUL_HOST}"
    fi
    
    if [ "$CONSUL_PORT" != "-1" ]; then
      cli_cmd="$cli_cmd --consul-port=${CONSUL_PORT}"
    fi
    
    if [ "$HEALTH_CHECK" = false ]; then
      cli_cmd="$cli_cmd --skip-health-check"
    fi
    
    # Execute registration command
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

# Start the module in the background with separate HTTP and gRPC ports
echo "Starting module with HTTP port ${MODULE_HTTP_PORT} and gRPC port ${MODULE_GRPC_PORT}..."
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -Dquarkus.http.port=${MODULE_HTTP_PORT} -Dquarkus.grpc.server.port=${MODULE_GRPC_PORT} -jar /deployments/quarkus-run.jar &
MODULE_PID=$!

# Give the module a moment to start up
sleep 10

# Register the module (CLI will handle health checks)
if register_module; then
  # Keep the module running in foreground
  wait $MODULE_PID
else
  # Kill the module if registration failed
  kill $MODULE_PID
  exit 1
fi