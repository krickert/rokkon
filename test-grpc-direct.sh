#!/bin/bash

echo "Testing Dynamic gRPC Direct Call"
echo "================================"

# Check if echo service is healthy in Consul
echo "1. Checking test-echo service health..."
curl -s http://localhost:8500/v1/health/service/test-echo?passing=true | jq '.[0].Service | {ID, Address, Port, Meta}'

# Check engine's module dashboard
echo -e "\n2. Checking engine module dashboard..."
curl -s http://localhost:38082/api/v1/modules/dashboard | jq .

# Try to get the list of available pipelines
echo -e "\n3. Checking available pipelines..."
curl -s http://localhost:38082/api/v1/clusters/default/pipelines | jq 'length'

# Check if we can see the service through the engine's service discovery
echo -e "\n4. Checking engine's view of modules..."
curl -s http://localhost:38082/api/v1/modules | jq .

echo -e "\nDone! If test-echo appears in Consul but not in the engine's module list,"
echo "then the engine isn't discovering services from Consul dynamically."