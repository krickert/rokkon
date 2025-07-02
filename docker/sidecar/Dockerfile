FROM hashicorp/consul:latest

# Install Java for the registration CLI
USER root
RUN apk add --no-cache openjdk21-jre curl bash

# Copy the registration CLI
COPY cli/register-module/build/quarkus-app/quarkus-run.jar /usr/local/bin/register-module.jar

# Create registration script
COPY docker/sidecar/register-and-monitor.sh /usr/local/bin/register-and-monitor.sh
RUN chmod +x /usr/local/bin/register-and-monitor.sh

# Switch back to consul user
USER consul

ENTRYPOINT ["/usr/local/bin/register-and-monitor.sh"]