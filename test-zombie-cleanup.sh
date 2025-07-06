#!/bin/bash

# Test script to verify zombie cleanup functionality

echo "Testing zombie cleanup functionality..."

# Check if Consul is running (try agent port 8501, dev mode port 38500, and standard port 8500)
CONSUL_PORT=8500
if curl -s http://localhost:8501/v1/status/leader > /dev/null 2>&1; then
    CONSUL_PORT=8501
    echo "Using Consul agent (port 8501)"
elif curl -s http://localhost:38500/v1/status/leader > /dev/null 2>&1; then
    CONSUL_PORT=38500
    echo "Using Consul in dev mode (port 38500)"
elif curl -s http://localhost:8500/v1/status/leader > /dev/null 2>&1; then
    CONSUL_PORT=8500
    echo "Using Consul on standard port (8500)"
else
    echo "ERROR: Consul is not running. Please start Consul first."
    exit 1
fi

echo "✓ Consul is running"

# Create a stale service entry (Type 2 zombie)
echo ""
echo "Creating a stale service entry (Type 2 zombie)..."
curl -X PUT http://localhost:$CONSUL_PORT/v1/agent/service/register -d '{
  "ID": "test-zombie-module-12345",
  "Name": "test-zombie-module",
  "Tags": ["module", "test", "zombie"],
  "Address": "192.168.1.100",
  "Port": 39999,
  "Meta": {
    "moduleName": "test-zombie-module",
    "serviceType": "MODULE"
  },
  "Check": {
    "Name": "Module gRPC Health Check",
    "GRPC": "192.168.1.100:39999",
    "GRPCUseTLS": false,
    "Interval": "10s",
    "DeregisterCriticalServiceAfter": "60s"
  }
}'

echo ""
echo "✓ Created stale service entry: test-zombie-module-12345"

# List all services with module tag
echo ""
echo "Services with 'module' tag:"
curl -s http://localhost:$CONSUL_PORT/v1/catalog/services | jq 'to_entries[] | select(.value[] == "module") | .key'

# Trigger zombie cleanup via the API
echo ""
echo "Triggering zombie cleanup..."
curl -s -X POST http://localhost:39001/api/v1/modules/cleanup-zombies | jq .

# Wait a bit for cleanup to complete
sleep 2

# Check if the zombie was cleaned up
echo ""
echo "Checking if zombie was cleaned up..."
if curl -s http://localhost:$CONSUL_PORT/v1/catalog/service/test-zombie-module | jq -e 'length == 0' > /dev/null; then
    echo "✓ Zombie successfully cleaned up!"
else
    echo "✗ Zombie still exists. Cleanup may have failed."
    echo "Remaining instances:"
    curl -s http://localhost:$CONSUL_PORT/v1/catalog/service/test-zombie-module | jq '.[] | {ID: .ServiceID, Address: .ServiceAddress, Port: .ServicePort}'
fi

echo ""
echo "Test complete."