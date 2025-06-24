You are absolutely correct\! My apologies for the oversight. Apicurio Registry 3.x involves significant changes, including configuration keys and the API, and Quarkus's built-in Dev Services for Apicurio currently target Apicurio Registry 2.x.

This makes the **Compose Dev Services** approach even more crucial and the *primary* best practice for getting Apicurio Registry 3.x running seamlessly in your Quarkus dev mode.

Since you requested a PDF, I'll structure the instructions as such. Please note that I cannot directly generate a PDF file here. You'll need to copy and paste the content below into a document editor (like Word, Google Docs, or LaTeX editor) and then export it as a PDF.

-----

## Quarkus Multi-Module Project Dev Resources with Compose Dev Services

**Leveraging Docker Compose for a Fast and Integrated Development Environment**

This guide outlines the best practices for setting up a comprehensive development environment for your multi-module Quarkus project, integrating services like Consul, Kafka, Apicurio Registry v3, Moto (for S3/Glue), MongoDB, Grafana, Prometheus, and OpenSearch 3.0, primarily using Quarkus's **Compose Dev Services**.

-----

### **1. Understanding the Approach: Compose Dev Services**

Quarkus's Dev Services are designed to automatically provision and configure external services needed for development and testing. While Quarkus provides built-in Dev Services for many common components (like Kafka and MongoDB), they might not always align with specific version requirements (e.g., Apicurio v3) or offer the full orchestration capabilities of Docker Compose.

**Compose Dev Services** (available since Quarkus 3.3) provides the ideal solution. It allows Quarkus to read a `docker-compose.yml` file and manage the lifecycle of the services defined within it when you run `quarkus dev`. This means you can:

* **Specify Exact Versions:** Crucial for Apicurio Registry v3, OpenSearch 3.0, etc.
* **Centralize Service Definitions:** All your development infrastructure in one file.
* **Control Dependencies & Health Checks:** Ensure services start in the correct order and are truly ready.
* **Expose UIs:** Easily access web interfaces for Grafana, Prometheus, OpenSearch Dashboards.
* **Improve Startup Speed:** By leveraging Docker Compose's network and container reuse.

-----

### **2. Project Setup Prerequisites**

Before you begin, ensure you have:

* **Quarkus Project:** A multi-module Quarkus project.
* **JDK 17+**
* **Apache Maven** (or Gradle)
* **Docker Desktop** (or Podman) installed and running, with sufficient resources allocated.

-----

### **3. Adding the `quarkus-devservices-compose` Dependency**

In your main Quarkus application module (or a shared `pom.xml` if applicable across modules), add the following dependency:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-devservices-compose</artifactId>
    <scope>test</scope> </dependency>
```

-----

### **4. Creating the `docker-compose.yml` File**

Create a `docker-compose.yml` (or `compose.yml`) file at the **root** of your multi-module project. This file will define all your development services.

**Key Considerations for `docker-compose.yml`:**

* **Image Versions:** Explicitly define the desired image versions for Apicurio v3, OpenSearch 3.0, etc.
* **Ports:** Map container ports to your host machine for accessing UIs (Grafana, Prometheus, OpenSearch Dashboards) and direct client connections if needed.
* **Environment Variables:** Configure services using their specific environment variables.
* **Health Checks:** **Crucially**, define `healthcheck` sections for each service. Quarkus (via Compose Dev Services) will wait for these health checks to pass before considering the service ready, preventing your application from trying to connect prematurely.
* **`depends_on`:** Use `depends_on` to establish start-up order dependencies between services (e.g., Grafana depends on Prometheus).
* **Volumes:** Use volumes for persistent data (e.g., OpenSearch data) if you want the data to survive Docker container restarts.

**Example `docker-compose.yml`:**

```yaml
version: '3.8'

services:
  consul:
    image: hashicorp/consul:latest
    container_name: dev-consul
    ports:
      - "8500:8500" # Expose Consul UI/API
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    healthcheck:
      test: ["CMD", "consul", "members", "-h", "localhost"]
      interval: 5s
      timeout: 3s
      retries: 5

  kafka:
    image: apache/kafka:4.0.0
    container_name: dev-kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_CFG_NODE_ID=0
      - KAFKA_CFG_PROCESS_ROLES=controller,broker
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 # Use service name for internal network
      - KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka:9093
      - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true
    healthcheck:
      test: ["CMD-SHELL", "kafka-topics.sh --bootstrap-server localhost:9092 --list"]
      interval: 5s
      timeout: 3s
      retries: 5

  apicurio-registry:
    image: apicurio/apicurio-registry-mem:3.0.x # **Crucial: Use your specific Apicurio v3 image**
    container_name: dev-apicurio-registry
    ports:
      - "8080:8080" # Default Apicurio port
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/apis/registry/v3/system/health"] # V3 API path
      interval: 10s
      timeout: 5s
      retries: 5

  moto:
    image: motoserver/moto:latest # Or a specific version that supports S3/Glue
    container_name: dev-moto
    ports:
      - "5000:5000" # Default Moto port for S3, Glue etc.
    environment:
      - MOTO_SERVICES=s3,glue # Specify the AWS services you need
      - MOTO_HOST=0.0.0.0
      - MOTO_PORT=5000
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5000/moto-api/status"]
      interval: 5s
      timeout: 3s
      retries: 5

  mongodb:
    image: mongo:latest
    container_name: dev-mongodb
    ports:
      - "27017:27017"
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongo localhost:27017/admin --quiet
      interval: 10s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus:latest
    container_name: dev-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml # Link your Prometheus config
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5

  grafana:
    image: grafana/grafana:latest
    container_name: dev-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: password
    depends_on:
      prometheus:
        condition: service_healthy # Ensure Prometheus is ready
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  opensearch:
    image: opensearchproject/opensearch:3.0.0 # **Crucial: Specify OpenSearch v3**
    container_name: dev-opensearch
    ports:
      - "9200:9200" # REST API
      - "9600:9600" # Transport layer
    environment:
      discovery.type: single-node # For single-node dev setup
      OPENSEARCH_JAVA_OPTS: "-Xms512m -Xmx512m" # Adjust memory if needed
      bootstrap.memory_lock: "true" # Required for production, good for dev too
      OPENSEARCH_INITIAL_ADMIN_PASSWORD: adminpassword # Set a password for initial setup
      OPENSEARCH_INITIAL_ADMIN_USERNAME: admin # Set a username
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - opensearch-data:/usr/share/opensearch/data # Persist data
    healthcheck:
      test: ["CMD-SHELL", "curl -s -k -u admin:adminpassword https://localhost:9200/_cluster/health | grep -qc '\"status\":\"green\"'"] # Use https and credentials
      interval: 10s
      timeout: 5s
      retries: 5

  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:3.0.0 # Matching dashboards version
    container_name: dev-opensearch-dashboards
    ports:
      - "5601:5601"
    environment:
      OPENSEARCH_HOSTS: '["http://opensearch:9200"]' # Connect to the OpenSearch service by name
    depends_on:
      opensearch:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5601/api/status"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  opensearch-data:
```

**Note:** For Prometheus, you'll need a basic `prometheus.yml` file at your project root (or `src/main/docker/`):

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'quarkus-app'
    metrics_path: /q/metrics # Default Quarkus metrics endpoint
    static_configs:
      - targets: ['host.docker.internal:8080'] # Assuming your Quarkus app runs on 8080 on host
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

-----

### **5. Configuring `application.properties` for Compose Dev Services**

In your Quarkus application's `src/main/resources/application.properties` (or `application.yaml`), you need to:

1.  **Enable Compose Dev Services.**
2.  **Point to your `docker-compose.yml` file.**
3.  **Disable Quarkus's built-in Dev Services** for components already managed by your `docker-compose.yml` to prevent conflicts and duplicate containers.
4.  **Configure your application** to connect to the services using their *service names* defined in the `docker-compose.yml` (since they'll be on the same Docker network).

<!-- end list -->

```properties
# --- Compose Dev Services Configuration ---
quarkus.devservices.compose.enabled=true
# Point to your docker-compose file. Adjust path if not at project root.
quarkus.devservices.compose.file=${user.dir}/docker-compose.yml

# --- Disable Built-in Dev Services (if defined in docker-compose) ---
quarkus.kafka.devservices.enabled=false
quarkus.mongodb.devservices.enabled=false
# Since Apicurio v3 is not directly supported by built-in Dev Services, this isn't strictly
# necessary to disable, but good practice if a future Quarkus version adds v3 support.
# quarkus.apicurio-registry.devservices.enabled=false
quarkus.consul.devservices.enabled=false
# For Moto, the general AWS Dev Services might need to be disabled if it interferes
# with your explicit Moto setup in compose.
quarkus.amazon.devservices.enabled=false

# --- Quarkus Application Connections (using Docker Compose service names) ---
# Kafka
mp.messaging.connector.smallrye-kafka.bootstrap-servers=kafka:9092

# MongoDB
quarkus.mongodb.connection-string=mongodb://mongodb:27017

# Apicurio Registry v3
# This is the crucial part for v3. Ensure the URL points to the Apicurio service in compose.
quarkus.apicurio-registry.url=http://apicurio-registry:8080

# Consul
quarkus.consul.host=consul
quarkus.consul.port=8500

# Moto (for S3/Glue)
quarkus.s3.endpoint-override=http://moto:5000
quarkus.glue.endpoint-override=http://moto:5000
quarkus.aws.region=us-east-1 # Or your desired region for Moto
# Moto uses dummy credentials, but Quarkus AWS client often requires them
quarkus.aws.credentials.type=static
quarkus.aws.credentials.static-provider.access-key=test
quarkus.aws.credentials.static-provider.secret-key=test

# OpenSearch (your application would connect to this directly if needed)
# Example for a REST client, e.g., using Quarkus REST Client or similar
# Ensure your OpenSearch client library is configured to use the service name.
# For example, if using the official Java client for OpenSearch:
# opensearch.client.hosts=opensearch:9200
# opensearch.client.username=admin
# opensearch.client.password=adminpassword
```

-----

### **6. Running Your Quarkus Project in Dev Mode**

Navigate to your multi-module project's root directory in your terminal and run:

```bash
./mvnw quarkus:dev
# or
./gradlew quarkusDev
```

Quarkus will now:

1.  Detect your `docker-compose.yml` file.
2.  Start all the services defined within it.
3.  Wait for their health checks to pass.
4.  Launch your Quarkus application, automatically configuring it to connect to these services using their internal Docker network names.
5.  Provide hot-reloading and continuous testing as usual.

-----

### **7. Accessing Service UIs/Dashboards**

You can access the UIs of Grafana, Prometheus, and OpenSearch Dashboards directly via their mapped ports on your `localhost`:

* **Grafana:** `http://localhost:3000` (User: `admin`, Pass: `password` - as configured in `docker-compose.yml`)
* **Prometheus:** `http://localhost:9090`
* **OpenSearch Dashboards:** `http://localhost:5601`
* **Consul UI:** `http://localhost:8500`
* **Apicurio Registry UI:** `http://localhost:8080` (Apicurio has a UI at its root by default)

**Note on Proxy:** For development, direct port mapping (as shown in the `docker-compose.yml`) is usually sufficient and simpler than setting up an Nginx reverse proxy. If your needs become more complex (e.g., custom path routing for many services), then adding an Nginx container to your `docker-compose.yml` and configuring it as a reverse proxy (as demonstrated in the previous response) would be the next step.

-----

### **8. Benefits for FAST Testing**

* **One Command Startup:** A single `quarkus dev` command brings up your entire environment.
* **Consistency:** Everyone on the team uses the exact same service versions and configurations.
* **Rapid Iteration:** Changes in your code trigger Quarkus hot-reload, and your application connects to the stable, running Dev Services without needing to restart the containers.
* **Continuous Testing:** Your tests will automatically run against these live, containerized services, providing fast and reliable integration test feedback.
* **Resource Management:** Docker Compose efficiently manages container lifecycles, often reusing containers across `quarkus dev` sessions, reducing startup times after the initial pull.

By following these instructions, you'll have a robust, fast, and highly integrated development environment for your multi-module Quarkus project.