#!/bin/bash

echo "Direct test of duplicate prevention logic..."

# First, let's clean up all echo modules
echo "Cleaning up all echo modules..."
for module_id in $(curl -s http://localhost:8081/api/v1/global-modules | jq -r '.[] | select(.moduleName=="echo") | .moduleId'); do
    echo "Deregistering module: $module_id"
    curl -X DELETE http://localhost:8081/api/v1/global-modules/$module_id
done

echo -e "\nRegistering echo module for the first time..."
response1=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-impl",
    "host": "localhost",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {}
  }')

body1=$(echo "$response1" | head -n -1)
status1=$(echo "$response1" | tail -n 1)

echo "First registration - Status: $status1"
echo "$body1" | jq .

echo -e "\nAttempting to register the same module again..."
response2=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-impl",
    "host": "localhost",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {}
  }')

body2=$(echo "$response2" | head -n -1)
status2=$(echo "$response2" | tail -n 1)

echo "Second registration - Status: $status2"
echo "$body2" | jq .

if [ "$status1" == "200" ] && [ "$status2" == "409" ]; then
    echo -e "\n✅ Success! Duplicate prevention is working correctly"
elif [ "$status1" == "200" ] && [ "$status2" == "200" ]; then
    echo -e "\n❌ Error! Duplicate prevention is NOT working - both registrations succeeded"
else
    echo -e "\n⚠️  Unexpected result - Status1: $status1, Status2: $status2"
fi