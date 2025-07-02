# Rokkon Consul Seeder - Configuration Guide

## Table of Contents
- [Introduction](#introduction)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Command-Line Options](#command-line-options)
- [Configuration Files](#configuration-files)
- [Interactive Mode](#interactive-mode)
- [Common Use Cases](#common-use-cases)
- [Troubleshooting](#troubleshooting)
- [Quick Reference](#quick-reference)

## Introduction

The Rokkon Consul Seeder is a command-line tool designed to seed Consul with initial application configuration for the Rokkon Engine. It provides a robust way to manage configuration with features like:

- Loading configuration from files
- Importing and exporting configuration
- Interactive mode for easier configuration
- Validation of inputs to prevent injection attacks
- Support for overriding existing configuration

This tool is essential for setting up and maintaining the Rokkon Engine configuration in a Consul environment.

## Installation

### Prerequisites
- Java 21 or higher
- Consul server running and accessible
- Gradle (for building from source)

### Building from Source
```shell
# Clone the repository
git clone https://github.com/krickert/rokkon.git
cd rokkon

# Build the project
./gradlew :engine:seed-config:build

# The executable JAR will be available at:
# engine/seed-config/build/quarkus-app/quarkus-run.jar
```

### Running the Application
```shell
java -jar engine/seed-config/build/quarkus-app/quarkus-run.jar [options]
```

## Quick Start

### Basic Usage
Seed Consul with default configuration:
```shell
java -jar seed-config.jar
```

Seed Consul with custom configuration file:
```shell
java -jar seed-config.jar --config /path/to/config.properties
```

Use interactive mode:
```shell
java -jar seed-config.jar --interactive
```

## Command-Line Options

| Option | Description | Default Value |
|--------|-------------|---------------|
| `-h, --host` | Consul host | localhost |
| `-p, --port` | Consul port | 8500 |
| `--key` | Consul key path | config/application |
| `--force` | Force overwrite existing configuration | false |
| `-c, --config` | Path to configuration file | - |
| `--export` | Export configuration to file | - |
| `--import` | Import configuration from file | - |
| `-i, --interactive` | Start in interactive mode | false |
| `--validate` | Validate configuration only (don't write to Consul) | false |
| `--timeout` | Timeout for Consul operations in seconds | 5 |
| `--help` | Show help message | - |

### Examples

Connect to a remote Consul server:
```shell
java -jar seed-config.jar --host consul.example.com --port 8500
```

Force overwrite existing configuration:
```shell
java -jar seed-config.jar --force
```

Use a custom key path:
```shell
java -jar seed-config.jar --key custom/config/path
```

Export configuration to a file:
```shell
java -jar seed-config.jar --export /path/to/export.properties
```

Import configuration from a file:
```shell
java -jar seed-config.jar --import /path/to/import.properties
```

Validate configuration without writing to Consul:
```shell
java -jar seed-config.jar --validate
```

## Configuration Files

The seed-config tool uses Java properties files for configuration. These files contain key-value pairs that define the Rokkon Engine configuration.

### Default Configuration

The default configuration includes settings for:
- Engine settings
- Consul cleanup settings
- Consul health check settings
- Module management settings
- Default cluster configuration

### File Format

Configuration files must be in Java properties format with the `.properties` extension. Here's an example:

```properties
# Rokkon Engine Configuration

# Engine settings
rokkon.engine.name=rokkon-engine
rokkon.engine.grpc-port=8081
rokkon.engine.rest-port=8080
rokkon.engine.debug=false

# Consul cleanup settings
rokkon.consul.cleanup.enabled=true
rokkon.consul.cleanup.interval=PT5M
rokkon.consul.cleanup.zombie-threshold=2m
rokkon.consul.cleanup.cleanup-stale-whitelist=true

# Consul health check settings
rokkon.consul.health.check-interval=10s
rokkon.consul.health.deregister-after=1m
rokkon.consul.health.timeout=5s

# Module management settings
rokkon.modules.auto-discover=false
rokkon.modules.service-prefix=module-
rokkon.modules.require-whitelist=true
rokkon.modules.connection-timeout=PT30S
rokkon.modules.max-instances-per-module=10

# Default cluster configuration
rokkon.default-cluster.name=default
rokkon.default-cluster.auto-create=true
rokkon.default-cluster.description=Default cluster for Rokkon pipelines
```

### Configuration Keys

Keys must follow these rules:
- Can contain alphanumeric characters, dots, hyphens, and underscores
- Cannot be null or empty
- Should follow the hierarchical naming convention (e.g., `rokkon.engine.name`)

### Default Configuration Location

The default configuration file is located at:
```
~/.rokkon/rokkon-config.properties
```

## Interactive Mode

The interactive mode provides a command-line interface for configuring the Consul seeder. To start interactive mode:

```shell
java -jar seed-config.jar --interactive
```

### Available Commands

| Command | Description |
|---------|-------------|
| `help` | Show help information |
| `list` | List all configuration entries |
| `get <key>` | Get the value of a configuration entry |
| `set <key> <value>` | Set a configuration entry |
| `remove <key>` | Remove a configuration entry |
| `clear` | Clear all configuration entries |
| `consul` | Configure Consul connection settings |
| `exit`, `quit` | Exit interactive mode |

### Example Session

```
=== Rokkon Consul Seeder Interactive Mode ===
Type 'help' for available commands, 'exit' to quit
rokkon> list
Configuration entries:
  rokkon.consul.cleanup.cleanup-stale-whitelist = true
  rokkon.consul.cleanup.enabled = true
  ...

rokkon> set rokkon.engine.debug true
Set rokkon.engine.debug = true

rokkon> get rokkon.engine.debug
rokkon.engine.debug = true

rokkon> consul
=== Consul Connection Configuration ===
Consul host [localhost]: consul.example.com
Consul port [8500]: 
Consul key path [config/application]: custom/config/path
Consul connection configuration updated

rokkon> exit
Exiting interactive mode
```

## Common Use Cases

### Initial Setup

For initial setup of a new Rokkon Engine instance:

```shell
# Create a configuration file
java -jar seed-config.jar --export initial-config.properties

# Edit the configuration file with your settings
# Then seed Consul with the updated configuration
java -jar seed-config.jar --import initial-config.properties --force
```

### Configuration Updates

To update existing configuration:

```shell
# Export current configuration
java -jar seed-config.jar --export current-config.properties

# Edit the configuration file
# Then update Consul with the changes
java -jar seed-config.jar --import current-config.properties --force
```

### Environment-Specific Configuration

For different environments (dev, staging, prod):

```shell
# Development
java -jar seed-config.jar --config dev-config.properties --key config/dev/application

# Staging
java -jar seed-config.jar --config staging-config.properties --key config/staging/application

# Production
java -jar seed-config.jar --config prod-config.properties --key config/prod/application
```

### CI/CD Pipeline Integration

For CI/CD pipelines, you can use the validate option to check configuration before deployment:

```shell
# Validate configuration
java -jar seed-config.jar --import pipeline-config.properties --validate

# If validation passes, seed Consul
java -jar seed-config.jar --import pipeline-config.properties --force
```

## Troubleshooting

### Common Issues

#### Connection Refused
```
Error: Connection refused
```
**Solution**: Ensure Consul is running and accessible at the specified host and port.

#### Invalid Configuration
```
Error: Invalid configuration key format
```
**Solution**: Ensure all keys in your configuration file follow the required format (alphanumeric, dots, hyphens, underscores).

#### Permission Denied
```
Error: Permission denied
```
**Solution**: Ensure you have write permissions for the export file location or read permissions for the import file.

#### Timeout
```
Error: Timeout storing configuration
```
**Solution**: Increase the timeout value using the `--timeout` option.

### Logging

To enable debug logging for more detailed information:

```shell
java -Dquarkus.log.level=DEBUG -jar seed-config.jar
```

## Quick Reference

### One-Liners for DevOps

Seed with default configuration:
```shell
java -jar seed-config.jar --force
```

Export current configuration:
```shell
java -jar seed-config.jar --export backup-$(date +%Y%m%d).properties
```

Import and validate configuration:
```shell
java -jar seed-config.jar --import new-config.properties --validate
```

Connect to remote Consul:
```shell
java -jar seed-config.jar --host consul.example.com --port 8500 --force
```

Interactive mode with custom config:
```shell
java -jar seed-config.jar --config base-config.properties --interactive
```

### Environment Variables

You can set default values for Consul host and port using environment variables:

```shell
export CONSUL_HOST=consul.example.com
export CONSUL_PORT=8500
java -jar seed-config.jar
```

### Docker Example

```shell
docker run -e CONSUL_HOST=consul -e CONSUL_PORT=8500 \
  -v /path/to/config:/config \
  rokkon/seed-config --config /config/my-config.properties --force
```

---

For more information, please refer to the [Rokkon Engine documentation](https://rokkon.example.com/docs).