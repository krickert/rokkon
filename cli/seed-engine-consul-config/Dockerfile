FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the built application
COPY build/quarkus-app/ ./build/quarkus-app/

# Copy seed data
COPY seed-data.json ./seed-data.json

# The command is specified in docker-compose.yml
ENTRYPOINT []