#!/bin/bash
set -e

# Configuration with defaults
MODULE_HOST=${MODULE_HOST:-0.0.0.0}
MODULE_PORT=${MODULE_PORT:-39100}  # Default unified port for echo module (391xx convention)
# With unified server, HTTP and gRPC use the same port
MODULE_HTTP_PORT=${MODULE_HTTP_PORT:-${MODULE_PORT}}
MODULE_GRPC_PORT=${MODULE_GRPC_PORT:-${MODULE_PORT}}  # Same as HTTP port
ENGINE_HOST=${ENGINE_HOST:-localhost}
ENGINE_PORT=${ENGINE_PORT:-8081}
CONSUL_HOST=${CONSUL_HOST:-""}
CONSUL_PORT=${CONSUL_PORT:-"-1"}
HEALTH_CHECK=${HEALTH_CHECK:-true}
MAX_RETRIES=${MAX_RETRIES:-3}
STARTUP_TIMEOUT=${STARTUP_TIMEOUT:-60}
CHECK_INTERVAL=${CHECK_INTERVAL:-5}
SHUTDOWN_ON_REGISTRATION_FAILURE=${SHUTDOWN_ON_REGISTRATION_FAILURE:-true}

# Function to register module with retries
register_module() {
  local retry_count=0
  local success=false

  while [ $retry_count -lt $MAX_RETRIES ] && [ "$success" = false ]; do
    echo "Registering module with engine (attempt $((retry_count+1))/${MAX_RETRIES})..."
    
    # Build CLI command with all options (unified server uses same port for both)
    local cli_cmd="pipeline register --module-host=${MODULE_HOST} --module-port=${MODULE_PORT} --engine-host=${ENGINE_HOST} --engine-port=${ENGINE_PORT}"
    
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

# Start the module in the background with unified server port
echo "Starting module with unified server on port ${MODULE_PORT}..."
# With unified server, we only need to set the HTTP port (gRPC will use the same port)
java ${JAVA_OPTS} ${JAVA_OPTS_APPEND} -Dquarkus.http.port=${MODULE_PORT} -jar /deployments/quarkus-run.jar &
MODULE_PID=$!

# Give the module a moment to start up
sleep 5

# Register the module (CLI will handle health checks)
if register_module; then
  echo "Module registered successfully!"
  # Keep the module running in foreground
  wait $MODULE_PID
else
  if [ "$SHUTDOWN_ON_REGISTRATION_FAILURE" = "true" ]; then
    echo "Registration failed. Shutting down module as SHUTDOWN_ON_REGISTRATION_FAILURE=true"
    kill $MODULE_PID
    exit 1
  else
    echo "Registration failed, but keeping module running as SHUTDOWN_ON_REGISTRATION_FAILURE=false"
    echo "Module is available for manual registration or debugging"
    # Keep the module running in foreground
    wait $MODULE_PID
  fi
fi