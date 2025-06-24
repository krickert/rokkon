# Rokkon Engine Docker Build Guide

This document provides instructions for building Docker images for the Rokkon Engine. It is intended for DevOps engineers who need to build and customize Docker images for the Rokkon Engine.

## Available Docker Images

The Rokkon Engine follows Quarkus standards for Docker images. The following Docker image types are available:

1. **Development Mode** (`Dockerfile.dev`): For development and testing with live coding
2. **JVM Mode** (`Dockerfile.jvm`): Standard JVM-based container for production
3. **Native Mode** (`Dockerfile.native`): GraalVM native image for faster startup and lower memory usage
4. **Native Micro Mode** (`Dockerfile.native-micro`): Minimal native image with distroless base
5. **Legacy JAR Mode** (`Dockerfile.legacy-jar`): Traditional fat JAR deployment

All Docker files are located in the `src/main/docker` directory.

## Building Docker Images

### Prerequisites

- Docker installed on your build machine
- Java 21 or later (for building the application)
- Gradle 8.0 or later (for building the application)

### Building the Application

Before building Docker images, you need to build the application:

```bash
# From the project root
./gradlew build
```

### Building Development Image

The development image is designed for development and testing with live coding:

```bash
# From the project root
docker build -f rokkon-engine/src/main/docker/Dockerfile.dev -t rokkon/rokkon-engine:dev rokkon-engine
```

### Building JVM Image (Recommended for Production)

The JVM image is the standard production image:

```bash
# From the project root
docker build -f rokkon-engine/src/main/docker/Dockerfile.jvm -t rokkon/rokkon-engine:latest rokkon-engine
```

### Building Native Image

The native image provides faster startup and lower memory usage:

```bash
# First, build the native executable
./gradlew build -Dquarkus.package.type=native

# Then build the Docker image
docker build -f rokkon-engine/src/main/docker/Dockerfile.native -t rokkon/rokkon-engine:native rokkon-engine
```

### Building Native Micro Image

The native micro image is a minimal image based on distroless:

```bash
# First, build the native executable
./gradlew build -Dquarkus.package.type=native

# Then build the Docker image
docker build -f rokkon-engine/src/main/docker/Dockerfile.native-micro -t rokkon/rokkon-engine:native-micro rokkon-engine
```

### Building Legacy JAR Image

The legacy JAR image uses a traditional fat JAR:

```bash
# First, build the legacy JAR
./gradlew build -Dquarkus.package.type=legacy-jar

# Then build the Docker image
docker build -f rokkon-engine/src/main/docker/Dockerfile.legacy-jar -t rokkon/rokkon-engine:legacy-jar rokkon-engine
```

## Customizing Docker Builds

### Common Customization Options

You can customize the Docker build process by modifying the Dockerfile or passing build arguments:

```bash
docker build -f rokkon-engine/src/main/docker/Dockerfile.jvm \
  --build-arg JAVA_PACKAGE="openjdk-17" \
  -t rokkon/rokkon-engine:custom rokkon-engine
```

### Customizing the Development Dockerfile

The development Dockerfile (`Dockerfile.dev`) can be customized for your development environment:

- **Base Image**: Change the base image for different Java versions
- **Dependencies**: Add additional dependencies needed for development
- **Ports**: Modify exposed ports for your environment
- **Volumes**: Adjust volume mounts for source code and Gradle cache

### Customizing Production Dockerfiles

Production Dockerfiles can be customized for your production environment:

- **Memory Settings**: Adjust JVM memory settings
- **GC Options**: Configure garbage collection
- **Security**: Add security-related configurations
- **Monitoring**: Add monitoring agents or tools

## Docker Image Structure

### Development Image

The development image includes:
- Full JDK for compilation
- Gradle for building
- Source code mounted as volumes
- Live coding enabled

### Production Images

Production images include:
- Only the necessary runtime components
- Optimized for size and startup time
- Security hardening
- Production-ready configurations

## Best Practices

1. **Tag Images Properly**: Use meaningful tags for your images (e.g., `latest`, `dev`, `1.0.0`)
2. **Use Multi-Stage Builds**: For smaller production images
3. **Scan Images**: Use security scanning tools to check for vulnerabilities
4. **Document Customizations**: Keep track of any customizations made to Dockerfiles
5. **Test Images**: Always test built images before deployment

## Troubleshooting Build Issues

### Common Build Problems

1. **Build Fails with Memory Errors**:
   - Increase Docker memory allocation
   - Add `--memory=4g` to Docker build command

2. **Native Image Build Fails**:
   - Ensure GraalVM is properly installed
   - Check for unsupported dependencies

3. **Image Size Too Large**:
   - Use multi-stage builds
   - Remove unnecessary dependencies
   - Use smaller base images

### Getting Help

If you encounter issues building Docker images, check:
- Quarkus documentation: https://quarkus.io/guides/building-native-image
- Docker documentation: https://docs.docker.com/engine/reference/builder/
- Project README and ARCHITECTURE_AND_PLAN.md files