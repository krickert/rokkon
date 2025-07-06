# Pipeline Engine: Extending Quarkus Dev UI for Development Operations

## Introduction

This document outlines the architectural approach for integrating development-specific operational functionalities directly into the existing Quarkus Dev UI. Instead of a separate frontend application for development tasks, these features will be exposed as custom extensions within the Quarkus Dev UI, leveraging its built-in capabilities for live coding and developer tooling. This approach provides a tightly integrated experience for rapid development and environment management.

## Goals

*   **Seamless Integration:** Provide development operations directly within the familiar Quarkus Dev UI.
*   **Centralized Dev Control:** Enable developers to manage the local Pipeline Engine environment (e.g., starting/stopping components, deploying/undeploying modules, seeding configurations) through a web interface.
*   **Leverage Quarkus Features:** Utilize Quarkus's native support for Dev UI extensions, avoiding the need for a separate frontend framework (like Vue.js) for these specific development tasks.
*   **Dev-Mode Only:** Ensure these functionalities are only available and active when the Pipeline Engine is running in Quarkus development mode.

## Architectural Overview

Extending the Quarkus Dev UI involves creating a Quarkus extension that contributes new pages, cards, or sections to the Dev UI. These UI components will interact with new REST endpoints exposed by the Pipeline Engine's backend, which in turn will orchestrate the underlying development operations.

```mermaid
graph TD
    subgraph "Developer Workstation"
        Browser[Web Browser] -- HTTP/S --> QuarkusDevUI[Quarkus Dev UI]
    end

    subgraph "Pipeline Engine (Quarkus Application - Dev Mode)"
        QuarkusDevUI -- REST API --> PipelineEngineBackend[Pipeline Engine Backend]
        PipelineEngineBackend -- Internal Calls --> DevOperationsService[New: Dev Operations Service]
        DevOperationsService -- Executes --> DevScripts[Existing: dev.sh / Gradle Tasks]
        QuarkusDevUI -- Served by --> DevUIExtension[New: Custom Dev UI Extension]
    end

    subgraph "External Services (Managed by dev.sh)"
        Consul[Consul Container]
        Kafka[Kafka Container (Optional)]
        Modules[Module Containers]
    end

    DevScripts -- Controls --> Consul
    DevScripts -- Controls --> Kafka
    DevScripts -- Controls --> Modules

    classDef frontend fill:#ADD8E6,stroke:#333,stroke-width:2px;
    classDef backend fill:#90EE90,stroke:#333,stroke-width:2px;
    classDef external fill:#FFD700,stroke:#333,stroke-width:2px;

    class QuarkusDevUI frontend;
    class PipelineEngineBackend,DevOperationsService,DevUIExtension backend;
    class Consul,Kafka,Modules external;
```

## Implementing Quarkus Dev UI Extensions

1.  **Quarkus Extension Module:** A new Quarkus extension module (e.g., `engine/dev-ui-extension`) will be created. This module will contain the necessary classes to contribute to the Dev UI.
2.  **Dev UI Pages/Cards:**
    *   Quarkus Dev UI extensions typically use `io.quarkus.devui.spi.page.DevUIPage` and related annotations (`@Page`, `@Section`, `@Card`) to define new UI elements.
    *   These UI elements can be simple HTML/JavaScript pages or more complex components rendered using templating engines or even small embedded web frameworks if needed (though for simple controls, direct HTML/JS is often sufficient).
    *   The UI will contain buttons, forms, and status displays to interact with the backend operations.
3.  **Backend REST Endpoints:**
    *   New JAX-RS (REST) endpoints will be created within the Pipeline Engine's main application (or a dedicated service within it) to handle requests from the Dev UI.
    *   These endpoints will encapsulate the logic for executing development tasks, similar to the `Dev Operations Service` described in the previous Dev Dashboard plan.
    *   Example endpoints:
        *   `POST /q/dev/pipeline-ops/start-consul`
        *   `POST /q/dev/pipeline-ops/seed-consul`
        *   `POST /q/dev/pipeline-ops/deploy-module/{moduleName}`
        *   `DELETE /q/dev/pipeline-ops/undeploy-module/{moduleName}`
        *   `POST /q/dev/pipeline-ops/stop-all`
        *   `GET /q/dev/pipeline-ops/status`
4.  **Executing `dev.sh` Functionalities:**
    *   The backend REST endpoints will use Java's `ProcessBuilder` or similar mechanisms to execute the underlying `dev.sh` script or specific Gradle tasks.
    *   **Asynchronous Execution:** Long-running operations should be executed asynchronously to prevent UI freezes and provide real-time feedback. WebSockets could be used for streaming logs or status updates back to the Dev UI.
    *   **Error Handling:** Robust error handling and logging will be crucial for diagnosing issues during development operations.

## Dev-Mode Only Considerations

Quarkus Dev UI extensions are inherently designed to be active only in development mode. This ensures that development-specific functionalities are not exposed in production environments.

*   **Conditional Availability:** The Dev UI components and their associated backend endpoints will only be compiled and available when Quarkus is running in `dev` mode.
*   **Security:** While the Dev UI is typically accessed on `localhost` during development, it's good practice to ensure that the backend endpoints are only accessible from trusted sources or require basic authentication if exposed beyond `localhost`.

## Bundling and Deployment

This Dev UI extension will be a separate module within the Pipeline Engine's Gradle project. It will be a dependency of the main `engine/pipestream` module, ensuring it's included when the engine is run in development mode.

*   **Module Location:** `engine/dev-ui-extension` (or similar).
*   **Build:** Standard Quarkus extension build process.
*   **Deployment:** Automatically bundled with the `engine/pipestream` JAR when running `quarkus dev`.

## Dependencies for Quarkus Dev UI Extensions

To create a Quarkus extension that contributes to the Dev UI, you would typically need the following dependencies in your `deployment` module's `pom.xml` (or `build.gradle.kts`):

*   **`io.quarkus:quarkus-vertx-http-dev-ui-spi`**: This is the core SPI that provides the necessary interfaces and annotations for extending the Dev UI. It's essential for defining new Dev UI pages, cards, and sections.
*   **`io.quarkus:quarkus-vertx-http-deployment`**: While not directly for Dev UI extension, the Dev UI itself relies on Quarkus's HTTP server. This dependency is often pulled in transitively or explicitly needed for HTTP-related functionalities.
*   **`io.quarkus:quarkus-rest-server-spi-deployment`**: If your Dev UI extension needs to expose its own REST endpoints (though in our case, the Dev UI components will call REST endpoints in the main `pipestream` module), this dependency would be relevant.

## Conclusion

Integrating development operations directly into the Quarkus Dev UI provides a powerful and intuitive experience for Pipeline Engine developers. By leveraging Quarkus's extension mechanism and exposing `dev.sh` functionalities via dedicated REST endpoints, we can create a seamless environment for rapid iteration and effective management of the local development setup. This approach aligns with Quarkus's philosophy of developer productivity and keeps development-specific concerns separate from the main production frontend.