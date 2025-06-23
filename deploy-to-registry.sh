#!/bin/bash
set -e

# This script deploys to the nas.rokkon.com:5000 registry

echo "🚀 Deploying Rokkon to nas.rokkon.com:5000 registry"
echo "================================================="

# Run the main deploy script with push parameter
./deploy.sh push