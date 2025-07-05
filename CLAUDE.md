## Development Reminders
- Read through TESTING_STRATEGY.md after every compaction /compact command happens
- DO NOT BUILD DOCKER IMAGES WITH THE DOCKER COMMAND.  Use the gradle build or else it will not work right.
- Use the application.yml instead of application.properties
- Use yml over property files.
- According to the Quarkus standard, integration tests compile against the jar, and this is a normal place to put it. Quarkus does this out of the box.
- Create integration tests in the `src/integrationTest` directory without needing to modify the build configuration. Refer to `DEVELOPER_NOTES/quarkus_documentation/getting_started_testing_quarkus.adoc` for more information on testing.
- Run integration tests with the quarkusIntTest.. for example `./gradlew :engine:dynamic-grpc:quarkusIntTest`
- CRITICAL: Integration tests MUST use `@QuarkusIntegrationTest` annotation and run with `quarkusIntTest` task, NOT `integrationTest`
- Do not disable consul-config during real running of the engine.  Consul is critical for the app to run.

## Dev Mode Architecture
- Dev mode uses a two-container Consul setup: server (port 38500) + agent (host network on 8501)
- This mirrors production sidecar pattern - all components talk to localhost:8501
- Run with: `./gradlew :engine:pipestream:quarkusDev` - it auto-starts Consul
- See DEV_MODE_ARCHITECTURE.md for full details
- Consul UI available at http://localhost:38500 in dev mode
- Implementation: Gradle task starts Consul server, then agent with host networking, seeds config, then starts Quarkus
- Uses unified HTTP/gRPC server on single port (39001 in dev) for simpler architecture
- Docker client reconnection handled automatically during dev reloads

## Conversion and Naming Conventions
- We are slowly converting from using "rokkon" in the code to just "pipeline"
- Do not add "rokkon" as values or fields unless it's just the package name
- Module services will not end in -module as they are tagged