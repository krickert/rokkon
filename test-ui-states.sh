#!/bin/bash

echo "Testing UI button states..."

# First, deregister all echo modules
echo "Deregistering all echo modules..."
for module_id in $(curl -s http://localhost:8081/api/v1/global-modules | jq -r '.[] | select(.moduleName=="echo") | .moduleId'); do
    echo "Deregistering module: $module_id"
    curl -X DELETE http://localhost:8081/api/v1/global-modules/$module_id
done

echo -e "\nWait a moment for UI to update, then check the dashboard..."
echo "The echo module should show 'Whitelist for Clusters' button"
sleep 2

echo -e "\nPress Enter to whitelist the module again..."
read

# Re-register the echo module
echo "Whitelisting echo module..."
curl -X POST http://localhost:8081/api/v1/global-modules/register \
  -H "Content-Type: application/json" \
  -d '{
    "moduleName": "echo",
    "implementationId": "echo-impl",
    "host": "172.17.0.3",
    "port": 49090,
    "serviceType": "PIPELINE",
    "version": "1.0.0",
    "metadata": {}
  }' | jq .

echo -e "\nNow check the dashboard again..."
echo "The echo module should show 'Whitelisted for Clusters' button (disabled)"