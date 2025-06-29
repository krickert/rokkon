#!/bin/bash

# Test module registration with Rokkon Engine

set -e

echo "Testing Rokkon module auto-registration..."
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Start Consul
echo -e "${YELLOW}1. Starting Consul...${NC}"
docker run -d --rm \
  --name rokkon-consul \
  -p 8500:8500 \
  -p 8600:8600/udp \
  consul:1.19.2 agent -dev -ui -client=0.0.0.0

# Wait for Consul
sleep 5

# Check Consul is ready
if curl -f http://localhost:8500/v1/status/leader >/dev/null 2>&1; then
  echo -e "${GREEN}✓ Consul is ready${NC}"
else
  echo -e "${RED}✗ Consul failed to start${NC}"
  exit 1
fi

# Start Rokkon Engine
echo -e "\n${YELLOW}2. Starting Rokkon Engine...${NC}"
docker run -d --rm \
  --name rokkon-engine \
  -p 8081:8081 \
  -p 9081:9081 \
  -e CONSUL_HOST=host.docker.internal \
  -e CONSUL_PORT=8500 \
  -e QUARKUS_HTTP_PORT=8081 \
  -e QUARKUS_GRPC_SERVER_PORT=9081 \
  -e QUARKUS_CONSUL_CONFIG_AGENT_HOST=host.docker.internal \
  -e QUARKUS_CONSUL_CONFIG_AGENT_PORT=8500 \
  rokkon/engine:latest

# Wait for Engine
echo "Waiting for Engine to start..."
sleep 10

# Check Engine health
if curl -f http://localhost:8081/q/health >/dev/null 2>&1; then
  echo -e "${GREEN}✓ Engine is ready${NC}"
else
  echo -e "${RED}✗ Engine failed to start${NC}"
  docker logs rokkon-engine
  exit 1
fi

# Start Test Module with auto-registration
echo -e "\n${YELLOW}3. Starting Test Module with auto-registration...${NC}"
docker run -d --rm \
  --name rokkon-test-module \
  -p 49095:49095 \
  -p 39095:39095 \
  -e MODULE_HOST=host.docker.internal \
  -e MODULE_PORT=49095 \
  -e ENGINE_HOST=host.docker.internal \
  -e ENGINE_PORT=9081 \
  -e CONSUL_HOST=host.docker.internal \
  -e CONSUL_PORT=8500 \
  -e QUARKUS_HTTP_PORT=39095 \
  -e QUARKUS_GRPC_SERVER_PORT=49095 \
  rokkon/test-module:latest

# Monitor registration
echo -e "\n${YELLOW}4. Monitoring registration...${NC}"
echo "Watching test-module logs for registration..."
sleep 5

# Check logs
docker logs rokkon-test-module 2>&1 | grep -E "(Registering|registered|Registration)"

# Check module health
echo -e "\n${YELLOW}5. Checking module health...${NC}"
if curl -f http://localhost:39095/q/health >/dev/null 2>&1; then
  echo -e "${GREEN}✓ Test module is healthy${NC}"
else
  echo -e "${RED}✗ Test module health check failed${NC}"
fi

# Check gRPC health
echo -e "\n${YELLOW}6. Checking gRPC health...${NC}"
if command -v grpcurl >/dev/null 2>&1; then
  grpcurl -plaintext localhost:49095 grpc.health.v1.Health/Check || true
else
  echo "grpcurl not installed, skipping gRPC health check"
fi

# Check registration in Consul
echo -e "\n${YELLOW}7. Checking Consul services...${NC}"
curl -s http://localhost:8500/v1/agent/services | jq '.'

# Show summary
echo -e "\n${GREEN}=== Registration Test Summary ===${NC}"
echo -e "Consul UI: ${YELLOW}http://localhost:8500${NC}"
echo -e "Engine Health: ${YELLOW}http://localhost:8081/q/health${NC}"
echo -e "Test Module Health: ${YELLOW}http://localhost:39095/q/health${NC}"
echo ""
echo -e "${YELLOW}To view logs:${NC}"
echo "  docker logs rokkon-engine"
echo "  docker logs rokkon-test-module"
echo ""
echo -e "${YELLOW}To stop all containers:${NC}"
echo "  docker stop rokkon-test-module rokkon-engine rokkon-consul"