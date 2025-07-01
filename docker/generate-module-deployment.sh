#!/bin/bash
# Generate module-specific docker-compose file from template
# Usage: ./generate-module-deployment.sh <module-name> <grpc-port> <http-port>

set -e

# Check arguments
if [ $# -lt 3 ]; then
    echo "Usage: $0 <module-name> <grpc-port> <http-port> [output-dir]"
    echo "Example: $0 echo 49091 39091"
    echo "Example: $0 test-module 49095 39095 ./deployments"
    exit 1
fi

MODULE_NAME="$1"
GRPC_PORT="$2"
HTTP_PORT="$3"
OUTPUT_DIR="${4:-.}"
TEMPLATE_FILE="$(dirname "$0")/module-deployment-template.yml"
OUTPUT_FILE="$OUTPUT_DIR/docker-compose-${MODULE_NAME}.yml"

# Check if template exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "Error: Template file not found: $TEMPLATE_FILE"
    exit 1
fi

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

echo "Generating deployment file for module: $MODULE_NAME"
echo "gRPC Port: $GRPC_PORT"
echo "HTTP Port: $HTTP_PORT"
echo "Output: $OUTPUT_FILE"

# Generate the deployment file
sed -e "s/MODULE_NAME/${MODULE_NAME}/g" \
    -e "s/\${MODULE_GRPC_PORT:-9090}/\${MODULE_GRPC_PORT:-${GRPC_PORT}}/g" \
    -e "s/\${MODULE_HTTP_PORT:-8080}/\${MODULE_HTTP_PORT:-${HTTP_PORT}}/g" \
    "$TEMPLATE_FILE" > "$OUTPUT_FILE"

# Add module-specific configuration based on module name
case "$MODULE_NAME" in
    "echo")
        cat >> "$OUTPUT_FILE" << 'EOF'

      # Echo module specific configuration
      - TRANSFORM_TYPE=${TRANSFORM_TYPE:-none}
EOF
        ;;
    "test-module")
        # Insert test-module specific env vars before healthcheck
        sed -i '/healthcheck:/i\      # Test module specific configuration\n      - PROCESSING_MODE=${PROCESSING_MODE:-echo}\n      - ERROR_RATE=${ERROR_RATE:-0.0}\n      - PROCESSING_DELAY_MS=${PROCESSING_DELAY_MS:-0}\n      - VALIDATION_STRICT=${VALIDATION_STRICT:-false}' "$OUTPUT_FILE"
        ;;
    "chunker")
        sed -i '/healthcheck:/i\      # Chunker module specific configuration\n      - CHUNK_SIZE=${CHUNK_SIZE:-1000}\n      - CHUNK_OVERLAP=${CHUNK_OVERLAP:-200}' "$OUTPUT_FILE"
        ;;
    "parser")
        sed -i '/healthcheck:/i\      # Parser module specific configuration\n      - PARSER_TYPE=${PARSER_TYPE:-auto}\n      - MAX_FILE_SIZE_MB=${MAX_FILE_SIZE_MB:-100}' "$OUTPUT_FILE"
        ;;
    "embedder")
        sed -i '/healthcheck:/i\      # Embedder module specific configuration\n      - EMBEDDING_MODEL=${EMBEDDING_MODEL:-default}\n      - BATCH_SIZE=${BATCH_SIZE:-32}' "$OUTPUT_FILE"
        ;;
esac

echo "✅ Generated $OUTPUT_FILE"
echo ""
echo "To deploy the module, run:"
echo "  docker compose -f $OUTPUT_FILE up -d"
echo ""
echo "To stop the module, run:"
echo "  docker compose -f $OUTPUT_FILE down"