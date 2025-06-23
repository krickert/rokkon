# Consul Configuration Guide

## Overview

The engine-consul module uses a hybrid configuration approach:
- **Simple operational settings** are managed through Consul Config (consul-config extension)
- **Complex data structures** (pipelines, module registrations) remain in Consul KV store

## Configuration Properties

All configuration properties can be set in three ways:
1. **application.yml** - Default values
2. **Environment variables** - Override defaults
3. **Consul KV store** - Dynamic updates with hot reload

### Setting Configuration in Consul

Configuration properties are stored under `/config/application/` in Consul's KV store.

```bash
# Example: Change cleanup interval to 2 minutes
consul kv put config/application/rokkon.consul.cleanup.interval 2m

# Example: Disable automatic cleanup
consul kv put config/application/rokkon.consul.cleanup.enabled false

# Example: Change health check interval
consul kv put config/application/rokkon.consul.health.check-interval 30s
```

### Available Configuration Properties

#### Engine Configuration
```yaml
rokkon:
  engine:
    grpc-port: 49000              # gRPC server port
    rest-port: 8080               # REST API port  
    instance-id: engine-1         # Optional instance identifier
    debug: false                  # Enable debug logging
```

#### Consul Cleanup Configuration
```yaml
rokkon:
  consul:
    cleanup:
      enabled: true               # Enable/disable automatic cleanup
      interval: 5m                # How often cleanup runs
      delay: 1                    # Initial delay in minutes
      zombie-threshold: 2m        # Time before unhealthy = zombie (future)
      cleanup-stale-whitelist: true # Remove orphaned whitelist entries
```

#### Health Check Configuration
```yaml
rokkon:
  consul:
    health:
      check-interval: 10s         # How often Consul checks module health
      deregister-after: 1m        # Auto-deregister after this many failures
      timeout: 5s                 # Health check connection timeout
```

#### Module Management Configuration
```yaml
rokkon:
  modules:
    auto-discover: false          # Auto-discover modules from Consul
    service-prefix: "module-"     # Prefix for module services
    require-whitelist: true       # Require explicit whitelisting
    connection-timeout: 30s       # Timeout for initial validation
    max-instances-per-module: 10  # Max instances per module type
```

#### Default Cluster Configuration
```yaml
rokkon:
  default-cluster:
    name: default                 # Name of default cluster
    auto-create: true            # Create on startup
    description: "Default cluster for Rokkon pipelines"
```

## Hot Reload

Configuration changes are automatically detected and applied without restart:
- Consul is checked every 10 seconds for changes
- Changes take effect immediately
- Check logs for confirmation of config updates

## What Stays in KV Store

The following data remains in Consul KV store as JSON documents:
- `/rokkon/pipelines/{name}` - Pipeline configurations
- `/rokkon/modules/registered/{id}` - Module registration metadata  
- `/rokkon/clusters/{name}/enabled-modules/{id}` - Whitelist entries
- `/rokkon/clusters/{name}/pipelines/{name}` - Pipeline assignments

## Example: Tuning for Production

```bash
# Faster cleanup for high-turnover environments
consul kv put config/application/rokkon.consul.cleanup.interval 2m

# More aggressive health checks
consul kv put config/application/rokkon.consul.health.check-interval 5s
consul kv put config/application/rokkon.consul.health.deregister-after 30s

# Increase connection timeout for slow networks
consul kv put config/application/rokkon.modules.connection-timeout 60s
```

## Monitoring Configuration

View current configuration:
```bash
# List all rokkon config
consul kv get -recurse config/application/rokkon

# Get specific value
consul kv get config/application/rokkon.consul.cleanup.interval
```

## Testing Configuration Changes

1. Make a configuration change in Consul
2. Wait up to 10 seconds for detection
3. Check application logs for "Configuration updated" messages
4. Verify behavior changes (e.g., cleanup frequency)

## Troubleshooting

If configuration changes aren't being applied:
1. Check Consul connectivity
2. Verify consul-config is enabled in application.yml
3. Check for typos in configuration keys
4. Look for validation errors in logs
5. Ensure values are in correct format (durations: "5m", "30s", etc.)