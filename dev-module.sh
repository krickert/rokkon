#!/bin/bash
# Quick Module Startup Script for Testing
# Usage: ./dev-module.sh <module-name>
# Example: ./dev-module.sh echo

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

MODULE=$1

if [ -z "$MODULE" ]; then
    echo -e "${RED}Usage: $0 <module-name>${NC}"
    echo "Available modules: echo, chunker, parser, embedder, test-module"
    exit 1
fi

# Module ports (following 3xxxx/4xxxx pattern)
case $MODULE in
    echo)
        HTTP_PORT=39091
        GRPC_PORT=49091
        ;;
    chunker)
        HTTP_PORT=39092
        GRPC_PORT=49092
        ;;
    parser)
        HTTP_PORT=39093
        GRPC_PORT=49093
        ;;
    embedder)
        HTTP_PORT=39094
        GRPC_PORT=49094
        ;;
    test-module)
        HTTP_PORT=39090
        GRPC_PORT=49090
        ;;
    *)
        echo -e "${RED}Unknown module: $MODULE${NC}"
        exit 1
        ;;
esac

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

echo -e "${GREEN}🚀 Starting $MODULE module${NC}"
echo "HTTP Port: $HTTP_PORT"
echo "gRPC Port: $GRPC_PORT"
echo ""

cd "$SCRIPT_DIR"
QUARKUS_HTTP_PORT=$HTTP_PORT \
QUARKUS_GRPC_SERVER_PORT=$GRPC_PORT \
./gradlew :modules:$MODULE:quarkusDev