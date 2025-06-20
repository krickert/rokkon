#!/bin/bash

echo "Stopping all Rokkon modules..."

# Stop processes using pid files
if [ -d logs ]; then
    for pidfile in logs/*.pid; do
        if [ -f "$pidfile" ]; then
            module=$(basename "$pidfile" .pid)
            pid=$(cat "$pidfile")
            echo "Stopping $module (PID: $pid)..."
            kill $pid 2>/dev/null || true
            rm "$pidfile"
        fi
    done
fi

# Also kill any remaining gradle daemons for the modules
pkill -f "gradlew quarkusDev" || true

echo "All modules stopped."