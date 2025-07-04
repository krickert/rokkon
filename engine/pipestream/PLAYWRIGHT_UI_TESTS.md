# Playwright UI Testing Setup

## Summary

We've added Playwright testing capability to test the module deployment UI and SSE real-time updates.

## What Was Added

### 1. Dependencies
```gradle
// In build.gradle.kts
testImplementation("io.quarkiverse.playwright:quarkus-playwright:2.1.3")
testImplementation("com.microsoft.playwright:playwright:1.53.0")

// Changed lit from compileOnly to implementation 
implementation("org.mvnpm:lit") // Use version from Quarkus BOM (3.2.1)
```

### 2. Test Files

- **DashboardDevModeTest.java** - UI tests for dashboard functionality
  - Tests dashboard loading
  - Tests module deployment UI
  - Tests SSE connection
  - Tests deployment animation with rocket icon

- **PlaywrightInstaller.java** - Helper to install Playwright browsers

- **DevModeTestProfile.java** - Test profile for dev mode

- **README-UI-TESTS.md** - Documentation for running UI tests

### 3. Test Deployment Script

Created `test-deployment-ui.sh` to manually test:
- SSE event streaming
- Module deployment
- Real-time updates

### 4. Web Bundler Fix

Created `web-bundler.json` to fix lit bundling issue:
```json
{
  "bundle": {
    "esbuild": {
      "external": ["lit-html/is-server.js"]
    }
  }
}
```

## Running the Tests

### Prerequisites
1. Install Playwright browsers (one time):
   ```bash
   npx playwright install
   ```

2. Start dev mode:
   ```bash
   ./gradlew :engine:pipestream:quarkusDev
   ```

### Run UI Tests
```bash
# Run all UI tests
./gradlew :engine:pipestream:test -Dtest.ui.devmode=true --tests "*DevModeTest"

# Run specific test
./gradlew :engine:pipestream:test -Dtest.ui.devmode=true --tests "DashboardDevModeTest"
```

### Manual Testing
```bash
# Make script executable
chmod +x test-deployment-ui.sh

# Run manual test
./test-deployment-ui.sh
```

## What the Tests Verify

1. **Dashboard Loading**
   - Dashboard component loads
   - Navigation header is visible
   - Dev mode badge appears

2. **Module Deployment UI**
   - Deploy dropdown is visible in dev mode
   - Available modules are listed
   - Echo module can be selected

3. **SSE Connection**
   - SSE endpoint returns 200 OK
   - Content-Type is text/event-stream

4. **Deployment Animation**
   - Rocket icon appears during deployment
   - Progress bar is visible
   - Deployment card disappears after completion
   - Module appears in service list

## Known Issues

1. **Web Bundler Lit Issue**: The bundler has trouble with lit's `is-server.js` module. We've marked it as external in `web-bundler.json`.

2. **Test Isolation**: Tests require a running dev mode instance, so they're disabled by default using system properties.

3. **Browser Installation**: Playwright browsers need to be installed separately using `npx playwright install`.

## Next Steps

1. Add more comprehensive UI tests for:
   - Pipeline management
   - Cluster management
   - Service registration/deregistration

2. Set up CI job for UI testing with dedicated dev mode instance

3. Add visual regression testing for UI components

4. Consider adding accessibility testing with Playwright