#!/bin/bash
# Compile and run just the PipelineExecutorLiveIT test
javac -cp "$(./gradlew :testing:integration:dependencies --configuration testCompileClasspath | grep -A1000 'testCompileClasspath' | grep -E '^\+|^\\\\' | sed 's/[+\\]--- //g' | tr '\n' ':')" \
  src/test/java/com/rokkon/integration/PipelineExecutorLiveIT.java

java -cp "build/classes/java/test:$(./gradlew :testing:integration:dependencies --configuration testRuntimeClasspath | grep -A1000 'testRuntimeClasspath' | grep -E '^\+|^\\\\' | sed 's/[+\\]--- //g' | tr '\n' ':')" \
  org.junit.platform.console.ConsoleLauncher \
  --select-class com.rokkon.integration.PipelineExecutorLiveIT