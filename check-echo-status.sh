#!/bin/bash

echo "Checking Echo Module Status"
echo "==========================="

echo -e "\n1. Docker containers for echo module:"
docker ps --filter "name=echo" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo -e "\n2. Deployed modules from API:"
curl -s http://localhost:39001/api/v1/module-management/deployed | jq '.'

echo -e "\n3. Registered modules in engine:"
curl -s http://localhost:39001/api/v1/modules | jq '.module_services[] | select(.name | contains("echo"))'

echo -e "\n4. Check if ports are listening:"
for port in 39100 39101 39102; do
    if nc -z localhost $port 2>/dev/null; then
        echo "Port $port: LISTENING"
    else
        echo "Port $port: NOT LISTENING"
    fi
done