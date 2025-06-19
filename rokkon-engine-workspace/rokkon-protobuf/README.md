# Rokkon Protocol Buffers

This project contains the Protocol Buffer definitions for the Rokkon Engine.

## Usage

This is a pure proto definitions project. Other Rokkon modules should depend on this project and use Quarkus' `scan-for-proto` feature to generate their own gRPC stubs.

### In your Quarkus module:

1. Add dependency:
```xml
<dependency>
    <groupId>com.rokkon.pipeline</groupId>
    <artifactId>rokkon-protobuf</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

2. Configure proto scanning in `application.properties`:
```properties
quarkus.generate-code.grpc.scan-for-proto=com.rokkon.pipeline:rokkon-protobuf
```

## Building

```bash
./gradlew publishToMavenLocal
```

This will publish the proto files to your local Maven repository for use by other modules.