# UI Testing with Playwright

This module includes Playwright tests for UI testing, particularly for Pipeline Engine dev-mode features.

## Running UI Tests

### Prerequisites
1. Ensure the engine is running in dev mode: `./gradlew :engine:pipestream:quarkusDev`
2. Install Playwright browsers (first time only): 
   ```bash
   mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
   # or with gradle
   ./gradlew :engine:pipestream:test --tests "*.installPlaywright" 
   ```

### Running Dev Mode UI Tests
These tests are disabled by default since they require dev mode and expose dev-only features.

To run them:
```bash
# Run with system property
./gradlew :engine:pipestream:test -Dtest.ui.devmode=true --tests "DashboardDevModeTest"

# Or run all UI tests
./gradlew :engine:pipestream:test -Dtest.ui.devmode=true --tests "*DevModeTest"
```

### Debug Mode
To see the browser and debug tests visually:
```java
@WithPlaywright(debug = true, headless = false)
```

### What's Tested

1. **Dashboard Loading** - Verifies the Pipeline Engine dashboard loads correctly
2. **Dev Mode UI** - Checks that dev-mode features (deploy button) are visible
3. **SSE Connection** - Verifies Server-Sent Events connection for real-time updates
4. **Deployment Animation** - Tests the rocket animation during module deployment

### CI Considerations

These tests should NOT run in CI by default since:
- They require dev mode which exposes additional endpoints
- They test features that are development-only
- They require a running instance with specific configuration

Consider creating a separate CI job specifically for dev mode UI testing if needed.