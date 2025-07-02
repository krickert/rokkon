#!/bin/sh
set -e

echo "Starting Consul agent..."
consul agent -data-dir=/consul/data -retry-join=consul-server -bind=127.0.0.1 &
CONSUL_PID=$!

echo "Waiting for Consul agent to be ready..."
for i in $(seq 1 30); do
  if consul members 2>/dev/null; then
    echo "Consul agent is ready!"
    break
  fi
  echo "Waiting for Consul agent... ($i/30)"
  sleep 1
done

echo "Seeding configuration..."
# Use the consul CLI to set the configs
consul kv put config/application @/seed-data.json
echo "✓ Seeded config/application"

consul kv put config/prod "# Empty prod config"
echo "✓ Seeded config/prod"

echo "Configuration seeding complete!"
echo "Consul agent running with PID $CONSUL_PID"

# Keep the agent running
wait $CONSUL_PID