#!/bin/bash

echo "Testing Module Scale-Up Functionality"
echo "====================================="

# Test scaling up echo module
echo -e "\n1. Testing scale-up for echo module:"
curl -X POST http://localhost:39001/api/v1/module-management/echo/scale-up \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n"

echo -e "\n2. Checking deployed modules:"
curl -s http://localhost:39001/api/v1/module-management/deployed | jq '.'

echo -e "\n3. Checking Docker containers for echo module:"
docker ps --filter "name=echo" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo -e "\n4. Testing another scale-up:"
curl -X POST http://localhost:39001/api/v1/module-management/echo/scale-up \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n"

echo -e "\n5. Final container check:"
docker ps --filter "name=echo" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"