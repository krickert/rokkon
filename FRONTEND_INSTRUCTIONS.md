# Rokkon Engine Frontend Instructions

## Overview
The Rokkon Engine now includes a web dashboard that provides a user-friendly interface for monitoring the engine status and managing module registrations.

## Changes Made
1. Added a web frontend to the Rokkon Engine
2. Created a simple dashboard with Bootstrap styling
3. Implemented functionality to:
   - Check engine status via the ping endpoint
   - Register new modules via the registration API
   - Display registered modules

## Accessing the Dashboard
Once the Rokkon Engine is running, you can access the dashboard at:
```
http://localhost:8080
```

## Starting the Application
You can start the entire development environment using:
```bash
./start-dev-environment.sh
```

This script will:
1. Start Consul (if not already running)
2. Start all modules
3. Start the Rokkon Engine

Alternatively, you can start just the engine with:
```bash
cd rokkon-engine
./gradlew quarkusDev
```

## Dashboard Features

### Engine Status
The dashboard displays the current status of the engine, including:
- Engine API status (via ping endpoint)
- Consul connection status

### Module Registration
You can register new modules directly from the dashboard by filling out the registration form with:
- Module name
- Host and port
- Module type
- Version
- Cluster name (defaults to "default")

### Registered Modules
The dashboard displays a list of registered modules with their status.

## Troubleshooting
If you encounter issues with the dashboard:
1. Check that the engine is running (look for "Rokkon Engine: http://localhost:8080" in the startup output)
2. Verify that Consul is running (http://localhost:8500)
3. Check the browser console for any JavaScript errors
4. Review the engine logs in the logs/ directory

## Next Steps
This is a basic implementation of the dashboard. Future enhancements could include:
- Real-time updates via WebSocket
- More detailed module information
- Pipeline visualization and management
- Document processing monitoring