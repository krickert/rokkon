# Rokkon Engine Port Mapping

## Port Convention
- HTTP ports: 3XXXX (prefix with 3)
- gRPC ports: 4XXXX (prefix with 4)

## Service Port Assignments

### Infrastructure
| Service | HTTP Port | gRPC Port | Notes |
|---------|-----------|-----------|-------|
| Consul | 8500 | - | Standard Consul port |
| Rokkon Engine | 38080 | 49000 | Main engine |

### Modules
| Module | HTTP Port | gRPC Port | Notes |
|--------|-----------|-----------|-------|
| echo | 38081 | 49090 | Echo service |
| chunker | 38082 | 49091 | Document chunking |
| parser | 38083 | 49092 | Document parsing |
| embedder | 38084 | 49093 | Text embedding |
| test-module | 38085 | 49094 | Testing module |

### Future Modules
| Module | HTTP Port | gRPC Port | Notes |
|--------|-----------|-----------|-------|
| opensearch-sink | 39095 | 49095 | OpenSearch writer |
| kafka-reader | 39096 | 49096 | Kafka consumer |
| kafka-writer | 39097 | 49097 | Kafka producer |