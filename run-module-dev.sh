#!/bin/bash

# Simple script to run a module in dev mode with the dev profile
MODULE=$1

if [ -z "$MODULE" ]; then
    echo "Usage: ./run-module-dev.sh <module-name>"
    echo "Example: ./run-module-dev.sh echo"
    exit 1
fi

if [ ! -d "modules/$MODULE" ]; then
    echo "Module directory modules/$MODULE does not exist!"
    exit 1
fi

echo "Starting $MODULE in dev mode with profile 'dev'..."
cd modules/$MODULE
./gradlew quarkusDev -Dquarkus.profile=dev