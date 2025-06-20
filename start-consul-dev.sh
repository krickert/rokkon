#!/bin/bash

# Start Consul in dev mode
echo "Starting Consul in dev mode..."
docker run -d \
  --name consul-dev \
  -p 8500:8500 \
  -p 8600:8600/udp \
  hashicorp/consul:latest agent -dev -ui -client=0.0.0.0

echo "Consul started at http://localhost:8500"
echo "Consul UI available at http://localhost:8500/ui"
