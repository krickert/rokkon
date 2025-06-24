# Rokkon Engine: Module Deployment and Registration

Modules are the core workhorse components in the Rokkon Engine, performing tasks like data ingestion (connectors), transformation (pipeline steps), and loading (sinks). Deploying and registering these modules correctly is crucial for the engine to discover and utilize them in pipelines.

## Module Deployment Process

Modules are typically packaged as Docker containers, each running a gRPC service that implements the Rokkon `PipeStepProcessor` interface (and potentially other required interfaces).

1.  **Development and Packaging:**
    *   Developers create a module in any gRPC-supported language (Java/Quarkus, Python, Go, Node.js, etc.).
    *   The module implements the necessary gRPC services defined in `rokkon-protobuf`.
    *   A `Dockerfile` is created to package the module application, its dependencies, and any necessary startup scripts.
    *   Common practice includes:
        *   Exposing the module's gRPC port (e.g., `9090`).
        *   Setting up an entrypoint script (e.g., `module-entrypoint.sh`) that starts the gRPC service.

2.  **Building the Docker Image:**
    *   The module's Docker image is built (e.g., `docker build -t rokkon/python-parser-module:v1.2 .`).
    *   For Java/Quarkus modules, this often involves a multi-stage build (e.g., using `Dockerfile.jvm` or `Dockerfile.native`).
    *   The build process might also include copying shared scripts or CLI tools (like `rokkon-cli.jar`) into the image, as described in `DEVELOPER_NOTES/modules/README-registration.md`.

3.  **Pushing to a Container Registry:**
    *   The built Docker image is pushed to a container registry accessible by the deployment environment (e.g., Docker Hub, AWS ECR, Google GCR, Azure ACR, or a private registry like `nas.rokkon.com:5000` mentioned in `application.yml`).
    *   Example: `docker push rokkon/python-parser-module:v1.2`

4.  **Deployment to an Execution Environment:**
    *   **Docker Host:** `docker run -d -p 9090:9090 --name parser-instance-1 -e ENGINE_HOST=krick.rokkon.com rokkon/python-parser-module:v1.2`
    *   **Kubernetes:** A Kubernetes `Deployment` manifest is created:
        ```yaml
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: python-parser-deployment
        spec:
          replicas: 3 # Example: 3 instances of the parser module
          selector:
            matchLabels:
              app: python-parser
          template:
            metadata:
              labels:
                app: python-parser
                moduleType: parser # Custom label for easier discovery/management
            spec:
              containers:
              - name: python-parser-container
                image: rokkon/python-parser-module:v1.2
                ports:
                - containerPort: 9090 # gRPC port
                env:
                - name: MODULE_PORT
                  value: "9090"
                - name: ENGINE_HOST
                  value: "rokkon-engine-service.rokkon-namespace.svc.cluster.local" # K8s service name for engine
                - name: ENGINE_PORT
                  value: "49000" # Engine's gRPC registration port
                - name: CONSUL_HOST
                  value: "consul-service.consul-namespace.svc.cluster.local" # For direct Consul registration if used
                # Add other necessary environment variables for the module
                # (e.g., API keys, model paths, specific configurations)
                readinessProbe: # Example gRPC health probe
                  grpc:
                    port: 9090
                    service: grpc.health.v1.Health # Standard gRPC health check
                  initialDelaySeconds: 5
                  periodSeconds: 10
                livenessProbe:
                  grpc:
                    port: 9090
                    service: grpc.health.v1.Health
                  initialDelaySeconds: 15
                  periodSeconds: 20
        ```
    *   A Kubernetes `Service` might also be created to expose the module instances, though often the engine discovers individual pod IPs via Consul.

## Module Registration with Consul

Once a module container starts, it must register itself with the Rokkon Engine and/or Consul so it can be discovered and utilized in pipelines. The primary mechanism for this involves the module announcing its presence and capabilities.

**Role of the Engine CLI (`rokkon-cli`) / Entrypoint Script:**

As outlined in `DEVELOPER_NOTES/modules/README-registration.md`, an automated registration process is often facilitated by an entrypoint script (`module-entrypoint.sh`) within the module's container and a CLI tool (`rokkon-cli.jar`).

1.  **Module Application Startup:** The entrypoint script first starts the module's main gRPC application in the background.
    ```bash
    # module-entrypoint.sh (simplified)
    echo "Starting module application..."
    java -jar /deployments/my-module-app.jar & # Or python /app/main.py &
    MODULE_PID=$!
    ```

2.  **Health Check Wait:** The script waits for the module's gRPC service to become healthy. This is often done by polling a standard gRPC health check endpoint (`grpc.health.v1.Health/Check`) or a custom health endpoint on the module.
    ```bash
    # module-entrypoint.sh (simplified)
    # Wait for module to be healthy (e.g., using grpcurl or custom script)
    until grpcurl -plaintext localhost:${MODULE_PORT:-9090} grpc.health.v1.Health/Check ...; do
      echo "Module not healthy yet, waiting..."
      sleep 5
    done
    ```

3.  **Registration via `rokkon-cli`:** Once healthy, the script calls the `rokkon-cli register` command.
    ```bash
    # module-entrypoint.sh (simplified)
    echo "Registering module with engine..."
    java -jar /deployments/rokkon-cli.jar register \
      --module-host ${MODULE_CONTAINER_IP:-$(hostname -i)} \
      --module-port ${MODULE_PORT:-9090} \
      --module-type ${MODULE_TYPE:-"generic-module"} \
      --module-version ${MODULE_VERSION:-"1.0.0"} \
      --engine-host ${ENGINE_HOST} \
      --engine-port ${ENGINE_REGISTRATION_PORT:-49000} # Port for ModuleRegistrationService on engine
      # Potentially other parameters like --service-name, --tags, etc.
    ```
    *   The `rokkon-cli register` command is a client application that communicates with a `ModuleRegistrationService` (or a similar endpoint) hosted by the Rokkon Engine.

4.  **Rokkon Engine's `ModuleRegistrationService`:**
    *   This gRPC service on the Rokkon Engine receives the registration request from the `rokkon-cli`.
    *   **Validation:** It performs validation:
        *   Is the module type whitelisted or recognized?
        *   Does the module respond to a `RegistrationCheck` RPC call to verify its basic functionality and advertised capabilities? (The engine calls back to the module's `RegistrationCheck` gRPC method).
        *   Are required parameters present?
    *   **Consul Registration (Handled by Engine):** If validation passes, the Rokkon Engine (specifically, its `engine-consul` component, which is the sole writer to Consul) registers the module instance as a service in Consul. This includes:
        *   Service Name (e.g., `parser-module`, `embedder-v2-text`).
        *   Service ID (unique for each instance, e.g., `parser-module-instance-abc123`).
        *   IP Address and Port of the module instance.
        *   Tags (e.g., `module-type:parser`, `version:1.2`, `rokkon-pipeline-module`).
        *   Health Check Configuration for Consul (e.g., a gRPC health check pointing to the module's health service).

5.  **Direct Consul Registration (Alternative/Complementary):**
    *   In some setups, particularly if the `rokkon-cli` is not used or for non-containerized modules, modules might register themselves *directly* with Consul using Consul's HTTP API or a Consul client library.
    *   However, the described architecture (`rokkon-engine/README.md`, `engine/consul/README.md`) emphasizes that the **Rokkon Engine (via `engine-consul`) is the sole writer to Consul** to maintain control and consistency. The `rokkon-cli` calling the engine to perform the registration aligns with this principle. The CLI acts on behalf of the container to initiate the process with the central engine.

**Diagram of Registration Flow (Engine-Mediated):**

```mermaid
sequenceDiagram
    participant Container as Module Container
    participant EntrypointScript as module-entrypoint.sh
    participant ModuleApp as Module gRPC App
    participant RokkonCLI as rokkon-cli.jar
    participant RokkonEngine as Rokkon Engine (ModuleRegistrationService)
    participant EngineConsul as Engine-Consul Writer
    participant Consul as Consul Server

    Container ->> EntrypointScript: Start Script
    EntrypointScript ->> ModuleApp: Start Application (in background)
    loop Health Check Loop
        EntrypointScript ->> ModuleApp: Check Health (e.g., gRPC Health/Check)
        ModuleApp -->> EntrypointScript: Health Status
    end
    note right of EntrypointScript: Loop breaks once module is healthy
    EntrypointScript ->> RokkonCLI: Execute `register` command with module details
    RokkonCLI ->> RokkonEngine: gRPC Call: RegisterModuleRequest (module IP, port, type, etc.)

    RokkonEngine ->> ModuleApp: gRPC Call: RegistrationCheckRequest (callback to module)
    ModuleApp -->> RokkonEngine: RegistrationCheckResponse (healthy, capabilities)

    alt Module Validated
        RokkonEngine ->> EngineConsul: Request to register service in Consul
        EngineConsul ->> Consul: PUT /v1/agent/service/register (Service Definition)
        Consul -->> EngineConsul: Registration Successful
        EngineConsul -->> RokkonEngine: Ack
        RokkonEngine -->> RokkonCLI: RegisterModuleResponse (Success)
    else Module Invalid
        RokkonEngine -->> RokkonCLI: RegisterModuleResponse (Failure, reason)
    end

    RokkonCLI -->> EntrypointScript: Exit Status
    alt Registration Successful
        EntrypointScript ->> ModuleApp: Keep module running (e.g., wait on PID)
    else Registration Failed
        EntrypointScript ->> Container: Log error, potentially exit/retry
    end
```

## How This Ties Together with Consul

*   **Service Discovery:** Once registered in Consul, the module instance becomes discoverable by the Rokkon Engine (using Stork with Consul service discovery) and potentially other services. The engine uses this discovery to route `PipeDoc` traffic to healthy, available module instances when executing pipeline steps.
*   **Health Monitoring:** Consul actively monitors the health of registered module instances using the configured health checks. If an instance becomes unhealthy, Consul marks it as such, and the Rokkon Engine will stop sending traffic to it.
*   **Dynamic Scaling:** If more instances of a module are deployed (e.g., by scaling a Kubernetes Deployment), each new instance runs the entrypoint script and registers itself. Consul and the Rokkon Engine automatically become aware of the increased capacity. Similarly, when instances are terminated, they should ideally deregister from Consul (Kubernetes can also help with this via preStop hooks or Consul lifecycle management tools like `consul-k8s`).
*   **Configuration Source (Optional for Modules):** While the engine reads pipeline configurations from Consul, individual modules *can* also be designed to read their own specific, fine-grained configurations from Consul if needed, though often step-specific parameters are passed by the engine during the `ProcessData` call.

This deployment and registration mechanism ensures that the Rokkon Engine has an up-to-date view of all available processing resources, enabling it to dynamically orchestrate complex data pipelines. The use of a CLI tool and entrypoint script standardizes the registration process for modules, regardless of the language they are written in.

## Further Reading

*   **Initialization (`Initialization.md`):** Describes how the Rokkon Engine itself starts and interacts with Consul.
*   **Pipeline Design (`Pipeline_design.md`):** Explains how modules fit into the logical pipeline structure.
*   **`DEVELOPER_NOTES/modules/README-registration.md`:** Provides specific instructions and context for the automated module registration setup.
*   **`rokkon-engine/README.md` and `engine/consul/README.md`:** Detail the engine's role and its interaction with Consul, emphasizing the centralized write model.
*   **Consul Documentation on Services and Health Checks.**
*   **Kubernetes Documentation on Deployments, Services, and Probes.**
*   **gRPC Health Checking Protocol documentation.**
