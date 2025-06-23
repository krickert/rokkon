#!/bin/bash
set -e

# Configuration with defaults
MODULE_HOST=${MODULE_HOST:-0.0.0.0}
MODULE_PORT=${MODULE_PORT:-9090}
ENGINE_HOST=${ENGINE_HOST:-localhost}
ENGINE_PORT=${ENGINE_PORT:-8081}
CONSUL_HOST=${CONSUL_HOST:-""}
CONSUL_PORT=${CONSUL_PORT:-"-1"}
HEALTH_CHECK=${HEALTH_CHECK:-true}
MAX_RETRIES=${MAX_RETRIES:-3}
STARTUP_TIMEOUT=${STARTUP_TIMEOUT:-60}
CHECK_INTERVAL=${CHECK_INTERVAL:-5}

# Function to check if module is ready
check_module_ready() {
  # Use grpcurl to check health
  if grpcurl -plaintext ${MODULE_HOST}:${MODULE_PORT} grpc.health.v1.Health/Check &>/dev/null; then
    return 0
  else
    return 1
  fi
}

# Function to register module with retries
register_module() {
  local retry_count=0
  local success=false

  while [ $retry_count -lt $MAX_RETRIES ] && [ "$success" = false ]; do
    echo "Registering module with engine (attempt $((retry_count+1))/${MAX_RETRIES})..."
    
    # Build CLI command with all options
    local cli_cmd="rokkon register --module-host=${MODULE_HOST} --module-port=${MODULE_PORT} --engine-host=${ENGINE_HOST} --engine-port=${ENGINE_PORT}"
    
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

# Start the module in the background
echo "Starting module..."
java ${JAVA_OPTS} -jar /deployments/quarkus-run.jar &
MODULE_PID=$!

# Wait for module to be ready
echo "Waiting for module to be ready (timeout: ${STARTUP_TIMEOUT}s)..."
elapsed=0
while [ $elapsed -lt $STARTUP_TIMEOUT ]; do
  if check_module_ready; then
    echo "Module is ready!"
    break
  fi
  
  sleep $CHECK_INTERVAL
  elapsed=$((elapsed + CHECK_INTERVAL))
  echo "Waiting for module to be ready... ($elapsed/${STARTUP_TIMEOUT}s)"
done

if [ $elapsed -ge $STARTUP_TIMEOUT ]; then
  echo "Timeout waiting for module to be ready."
  kill $MODULE_PID
  exit 1
fi

# Register the module
if register_module; then
  # Keep the module running in foreground
  wait $MODULE_PID
else
  # Kill the module if registration failed
  kill $MODULE_PID
  exit 1
fi