# Port Assignments for Rokkon Engine

## Port Convention
- **HTTP ports**: 38xxx for engine, 39xxx for modules
- **gRPC ports**: 49xxx for all services
- **Debug ports**: 50xx for JDWP debugging

## Core Infrastructure
- **MongoDB**: 27017 (standard)
- **Consul**: 8500 (HTTP), 8600 (DNS/UDP)
- **OpenSearch**: 9200 (HTTP), 9300 (transport)

## Engine Services

### rokkon-engine (Main Engine)
- HTTP: 38090
- gRPC: 49000
- Debug: 5005

### engine-consul
- HTTP: 38091
- Debug: 5006

### engine-validators
- HTTP: 38093
- Debug: 5007

### engine-registration
- HTTP: 38094
- gRPC: 49094
- Debug: 5008

### engine-models
- HTTP: 38095
- Debug: 5009

## Module Services

### echo-module
- HTTP: 39090
- gRPC: 49090
- Debug: 5010

### parser-module
- HTTP: 39091
- gRPC: 49091
- Debug: 5011

### chunker-module
- HTTP: 39092
- gRPC: 49092
- Debug: 5012

### embedder-module
- HTTP: 39093
- gRPC: 49093
- Debug: 5013

### test-module
- HTTP: 39095
- gRPC: 49095
- Debug: 5014

## Commons & Libraries

### rokkon-commons
- HTTP: 38096 (if run standalone)
- Debug: 5015

## Reserved for Future Use
- HTTP: 38097-38099 (engine services)
- HTTP: 39096-39099 (additional modules)
- gRPC: 49096-49099 (additional services)
- Debug: 5016-5020