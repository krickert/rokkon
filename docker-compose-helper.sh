#!/bin/bash
# Helper script for Docker Compose operations

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Functions
show_status() {
    echo -e "\n${BLUE}🐳 Docker Compose Status:${NC}"
    docker compose ps
}

show_endpoints() {
    echo -e "\n${BLUE}🌐 Available Endpoints:${NC}"
    echo "  Consul UI:         http://localhost:8500/ui"
    echo "  Engine Dashboard:  http://localhost:38082/"
    echo "  Engine Health:     http://localhost:38082/q/health"
    echo "  Engine Swagger:    http://localhost:38082/swagger-ui"
    echo "  Echo Module:       http://localhost:39092/"
    echo ""
    echo -e "${BLUE}📡 Internal Network (rokkon-network):${NC}"
    echo "  Consul Server:     consul-server:8500"
    echo "  Engine (via sidecar): consul-agent-engine:38082/48082"
    echo "  Echo (via sidecar):   consul-agent-echo:39092/49092"
}

start_services() {
    echo -e "${BLUE}Starting Docker Compose environment...${NC}"
    docker compose up -d
    
    echo -e "${YELLOW}Waiting for services to be ready...${NC}"
    sleep 10
    
    # Check if engine is healthy
    echo -n "Checking engine health..."
    for i in {1..30}; do
        if curl -f http://localhost:38082/q/health >/dev/null 2>&1; then
            echo -e " ${GREEN}OK${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    
    show_status
    show_endpoints
}

stop_services() {
    echo -e "${YELLOW}Stopping Docker Compose environment...${NC}"
    docker compose down
    echo -e "${GREEN}✅ Services stopped${NC}"
}

clean_all() {
    echo -e "${RED}Cleaning up everything (including volumes)...${NC}"
    docker compose down -v
    echo -e "${GREEN}✅ All cleaned up${NC}"
}

view_logs() {
    SERVICE=${1:-}
    if [ -z "$SERVICE" ]; then
        docker compose logs -f
    else
        docker compose logs -f "$SERVICE"
    fi
}

# Main execution
case "${1:-}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        stop_services
        start_services
        ;;
    status)
        show_status
        show_endpoints
        ;;
    clean)
        clean_all
        ;;
    logs)
        view_logs "${2:-}"
        ;;
    help|--help|-h)
        echo "Usage: $0 [start|stop|restart|status|clean|logs [service]]"
        echo ""
        echo "Commands:"
        echo "  start    - Start all services"
        echo "  stop     - Stop all services"
        echo "  restart  - Restart all services"
        echo "  status   - Show service status and endpoints"
        echo "  clean    - Stop services and remove volumes"
        echo "  logs     - View logs (optionally specify service)"
        echo ""
        echo "Examples:"
        echo "  $0 start"
        echo "  $0 logs engine"
        echo "  $0 logs consul-agent-engine"
        ;;
    *)
        start_services
        ;;
esac