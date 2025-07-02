#!/bin/bash
set -e

# Script to set up automated module registration for a Pipeline module project
# Usage: ./setup-module-registration.sh <module-directory>

# Check if module directory is provided
if [ $# -lt 1 ]; then
  echo "Usage: $0 <module-directory>"
  echo "Example: $0 modules/my-new-module"
  exit 1
fi

MODULE_DIR=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Check if module directory exists
if [ ! -d "$ROOT_DIR/$MODULE_DIR" ]; then
  echo "Error: Module directory $MODULE_DIR does not exist"
  exit 1
fi

echo "Setting up automated module registration for $MODULE_DIR..."

# 1. Check if build.gradle.kts exists
GRADLE_FILE="$ROOT_DIR/$MODULE_DIR/build.gradle.kts"
if [ ! -f "$GRADLE_FILE" ]; then
  echo "Error: build.gradle.kts not found in $MODULE_DIR"
  exit 1
fi

# 2. Add Gradle tasks if they don't exist
if ! grep -q "copyModuleEntrypoint" "$GRADLE_FILE"; then
  echo "Adding Gradle tasks to $GRADLE_FILE..."
  
  # Find the right position to insert the tasks (before the last closing brace)
  LINE_NUM=$(grep -n "}" "$GRADLE_FILE" | tail -1 | cut -d: -f1)
  
  # Create temporary file with the tasks
  TMP_FILE=$(mktemp)
  cat > "$TMP_FILE" << 'EOF'

// Copy module entrypoint script and CLI jar for Docker build
tasks.register<Copy>("copyModuleEntrypoint") {
    from(rootProject.file("scripts/module-entrypoint.sh"))
    into(layout.buildDirectory)
    rename { "module-entrypoint.sh" }
}

tasks.register<Copy>("copyPipelineCli") {
    from(project(":engine:cli-register").tasks.named("quarkusBuild").map { it.outputs.files.singleFile })
    into(layout.buildDirectory)
    rename { "pipeline-cli.jar" }
}

tasks.named("quarkusBuild") {
    finalizedBy("copyModuleEntrypoint", "copyPipelineCli")
}
EOF

  # Insert the tasks before the last closing brace
  head -n $((LINE_NUM-1)) "$GRADLE_FILE" > "$GRADLE_FILE.new"
  cat "$TMP_FILE" >> "$GRADLE_FILE.new"
  tail -n $(($(wc -l < "$GRADLE_FILE") - LINE_NUM + 1)) "$GRADLE_FILE" >> "$GRADLE_FILE.new"
  mv "$GRADLE_FILE.new" "$GRADLE_FILE"
  rm "$TMP_FILE"
  
  echo "Gradle tasks added successfully."
else
  echo "Gradle tasks already exist, skipping..."
fi

# 3. Check if Dockerfile.jvm exists
DOCKERFILE="$ROOT_DIR/$MODULE_DIR/src/main/docker/Dockerfile.jvm"
if [ ! -f "$DOCKERFILE" ]; then
  echo "Warning: Dockerfile.jvm not found at $DOCKERFILE"
  echo "You will need to manually update your Dockerfile to include the entrypoint script."
else
  # 4. Update Dockerfile if needed
  if ! grep -q "module-entrypoint.sh" "$DOCKERFILE"; then
    echo "Updating Dockerfile..."
    
    # Check if grpcurl is already installed
    if ! grep -q "grpcurl" "$DOCKERFILE"; then
      # Find the line after FROM to add grpcurl installation
      FROM_LINE=$(grep -n "FROM" "$DOCKERFILE" | head -1 | cut -d: -f1)
      ENV_LINE=$(grep -n "ENV" "$DOCKERFILE" | head -1 | cut -d: -f1)
      INSERT_LINE=$((ENV_LINE + 1))
      
      # Insert grpcurl installation
      sed -i "${INSERT_LINE}i\\
# Install grpcurl for health checks\\
RUN curl -sSL https://github.com/fullstorydev/grpcurl/releases/download/v1.8.7/grpcurl_1.8.7_linux_x86_64.tar.gz | tar -xz -C /usr/local/bin" "$DOCKERFILE"
    fi
    
    # Find the line before ENTRYPOINT to add CLI and entrypoint script
    ENTRYPOINT_LINE=$(grep -n "ENTRYPOINT" "$DOCKERFILE" | head -1 | cut -d: -f1)
    
    # Insert CLI and entrypoint script before ENTRYPOINT
    sed -i "${ENTRYPOINT_LINE}i\\
# Copy the CLI tool\\
COPY --chown=185 build/pipeline-cli.jar /deployments/pipeline-cli.jar\\
\\
# Create a wrapper script for the CLI\\
RUN echo '#!/bin/bash' > /usr/local/bin/pipeline && \\\\\\
    echo 'java -jar /deployments/pipeline-cli.jar \"\$@\"' >> /usr/local/bin/pipeline && \\\\\\
    chmod +x /usr/local/bin/pipeline\\
\\
# Copy the entrypoint script\\
COPY --chown=185 build/module-entrypoint.sh /deployments/module-entrypoint.sh\\
RUN chmod +x /deployments/module-entrypoint.sh\\
\\
# Make sure to expose the gRPC port\\
EXPOSE 9090" "$DOCKERFILE"
    
    # Replace the ENTRYPOINT line
    sed -i "${ENTRYPOINT_LINE}s|.*|ENTRYPOINT [\"/deployments/module-entrypoint.sh\"]|" "$DOCKERFILE"
    
    echo "Dockerfile updated successfully."
  else
    echo "Dockerfile already updated, skipping..."
  fi
fi

# 5. Check if docker-build.sh exists
DOCKER_BUILD="$ROOT_DIR/$MODULE_DIR/docker-build.sh"
if [ ! -f "$DOCKER_BUILD" ]; then
  echo "Creating docker-build.sh..."
  
  # Create docker-build.sh
  cat > "$DOCKER_BUILD" << EOF
#!/bin/bash

# Build the CLI project first
echo "Building cli-register project..."
cd ../../
./gradlew :engine:cli-register:quarkusBuild

# Build the module application
echo "Building module..."
cd $MODULE_DIR
./gradlew clean build

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t pipeline/$(basename "$MODULE_DIR"):latest .

echo "Docker image built successfully!"
echo "Run with: docker run -i --rm -p 9090:9090 -p 8080:8080 -e ENGINE_HOST=host.docker.internal pipeline/$(basename "$MODULE_DIR"):latest"
EOF
  
  chmod +x "$DOCKER_BUILD"
  echo "docker-build.sh created successfully."
else
  # Update docker-build.sh if needed
  if ! grep -q "cli-register" "$DOCKER_BUILD"; then
    echo "Updating docker-build.sh..."
    
    # Create temporary file with updated content
    TMP_FILE=$(mktemp)
    cat > "$TMP_FILE" << EOF
#!/bin/bash

# Build the CLI project first
echo "Building cli-register project..."
cd ../../
./gradlew :engine:cli-register:quarkusBuild

# Build the module application
echo "Building module..."
cd $MODULE_DIR
./gradlew clean build

# Build Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.jvm -t pipeline/$(basename "$MODULE_DIR"):latest .

echo "Docker image built successfully!"
echo "Run with: docker run -i --rm -p 9090:9090 -p 8080:8080 -e ENGINE_HOST=host.docker.internal pipeline/$(basename "$MODULE_DIR"):latest"
EOF
    
    mv "$TMP_FILE" "$DOCKER_BUILD"
    chmod +x "$DOCKER_BUILD"
    echo "docker-build.sh updated successfully."
  else
    echo "docker-build.sh already updated, skipping..."
  fi
fi

echo ""
echo "Setup complete! The module in $MODULE_DIR is now configured for automated registration."
echo "To build and run the module:"
echo "  1. cd $MODULE_DIR"
echo "  2. ./docker-build.sh"
echo "  3. docker run -i --rm -p 9090:9090 -p 8080:8080 -e ENGINE_HOST=<engine-host> pipeline/$(basename "$MODULE_DIR"):latest"
echo ""
echo "For more information, see modules/README-registration.md"