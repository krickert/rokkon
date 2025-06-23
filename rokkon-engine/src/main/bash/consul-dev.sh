#!/bin/bash

# Script to manage Consul for development
# Usage: ./consul-dev.sh [start|stop|status|logs]

COMPOSE_FILE="docker-compose.yml"

# Ensure we're in the project root
cd "$(dirname "$0")/../../.." || exit 1

case "$1" in
    start)
        echo "Starting Consul Dev Service..."
        docker compose -f $COMPOSE_FILE up -d consul
        echo "Waiting for Consul to be ready..."
        sleep 3
        
        # Check if Consul is running
        if curl -s http://localhost:8500/v1/status/leader > /dev/null 2>&1; then
            echo "✅ Consul is running at http://localhost:8500"
            echo "   UI available at http://localhost:8500/ui"
        else
            echo "❌ Consul failed to start"
            docker compose -f $COMPOSE_FILE logs consul
            exit 1
        fi
        ;;
        
    stop)
        echo "Stopping Consul Dev Service..."
        docker compose -f $COMPOSE_FILE down
        echo "✅ Consul stopped"
        ;;
        
    status)
        if docker ps | grep -q rokkon-consul-dev; then
            echo "✅ Consul is running"
            echo "   Container: $(docker ps --filter name=rokkon-consul-dev --format 'table {{.Names}}\t{{.Status}}')"
            echo "   Leader: $(curl -s http://localhost:8500/v1/status/leader 2>/dev/null || echo 'Not available')"
        else
            echo "❌ Consul is not running"
        fi
        ;;
        
    logs)
        docker compose -f $COMPOSE_FILE logs -f consul
        ;;
        
    *)
        echo "Usage: $0 {start|stop|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start  - Start Consul container"
        echo "  stop   - Stop Consul container"
        echo "  status - Check Consul status"
        echo "  logs   - Follow Consul logs"
        exit 1
        ;;
esac