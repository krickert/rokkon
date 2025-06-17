# Proof of Concept: Moving Test Documents to Test Resources

This document provides a practical example of how to implement the recommended approach of moving test documents from `src/main/resources` to `src/test/resources` while maintaining compatibility and minimizing disruption.

## Directory Structure Changes

### Current Structure
```
test-utilities/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/rokkon/test/data/
│   │   │       ├── TestDocumentLoader.java
│   │   │       └── ...
│   │   └── resources/
│   │       └── test-data/
│   │           ├── document-metadata.csv
│   │           ├── source-documents/
│   │           └── tika/
│   └── test/
│       ├── java/
│       │   └── com/rokkon/test/data/
│       │       ├── TestDocumentLoaderTest.java
│       │       └── ...
│       └── resources/
│           └── (empty)
```

### Proposed Structure
```
test-utilities/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/rokkon/test/data/
│   │   │       ├── TestDocumentLoader.java
│   │   │       └── ...
│   │   └── resources/
│   │       └── (minimal production resources)
│   └── test/
│       ├── java/
│       │   └── com/rokkon/test/data/
│       │       ├── TestDocumentLoaderTest.java
│       │       └── ...
│       └── resources/
│           └── test-data/
│               ├── document-metadata.csv
│               ├── source-documents/
│               └── tika/
```

## Resource Loading Abstraction

Create a new utility class to abstract resource loading that works with both test and main resources:

```java
package com.rokkon.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

/**
 * Utility for loading resources that might be in either main or test resources.
 * This helps with the transition from main to test resources.
 */
public class ResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceLoader.class);
    
    /**
     * Attempts to load a resource from the classpath, trying both main and test paths.
     * 
     * @param resourcePath The path to the resource (e.g., "test-data/document-metadata.csv")
     * @return An Optional containing the InputStream if found, or empty if not found
     */
    public static Optional<InputStream> loadResource(String resourcePath) {
        ClassLoader classLoader = ResourceLoader.class.getClassLoader();
        
        // First try the direct path (works for both main and test resources)
        InputStream directStream = classLoader.getResourceAsStream(resourcePath);
        if (directStream != null) {
            LOG.debug("Found resource at direct path: {}", resourcePath);
            return Optional.of(directStream);
        }
        
        // If not found, try with test- prefix (for transition period)
        String testPath = "test-" + resourcePath;
        InputStream testStream = classLoader.getResourceAsStream(testPath);
        if (testStream != null) {
            LOG.debug("Found resource at test path: {}", testPath);
            return Optional.of(testStream);
        }
        
        LOG.warn("Resource not found in classpath: {}", resourcePath);
        return Optional.empty();
    }
    
    /**
     * Loads a resource as a byte array.
     * 
     * @param resourcePath The path to the resource
     * @return An Optional containing the byte array if found, or empty if not found
     */
    public static Optional<byte[]> loadResourceAsBytes(String resourcePath) {
        return loadResource(resourcePath).map(is -> {
            try {
                return is.readAllBytes();
            } catch (Exception e) {
                LOG.error("Error reading resource: {}", resourcePath, e);
                return null;
            } finally {
                try {
                    is.close();
                } catch (Exception e) {
                    LOG.warn("Error closing resource stream: {}", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Loads a resource as a string.
     * 
     * @param resourcePath The path to the resource
     * @return An Optional containing the string if found, or empty if not found
     */
    public static Optional<String> loadResourceAsString(String resourcePath) {
        return loadResourceAsBytes(resourcePath).map(bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
}
```

## Updated TestDocumentLoader

Modify the TestDocumentLoader class to use the new ResourceLoader:

```java
package com.rokkon.test.data;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.ByteString;
import com.rokkon.test.util.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for loading test data from protobuf binary files and creating test documents.
 * Updated to work with both main and test resources.
 */
public class TestDocumentLoader {
    private static final Logger LOG = LoggerFactory.getLogger(TestDocumentLoader.class);
    
    /**
     * Creates test documents from CSV metadata (CSV-driven approach).
     * Uses document-metadata.csv to load documents with full metadata.
     * 
     * @return List of PipeDoc objects with blob data for processing
     */
    public static List<PipeDoc> createTestDocumentsFromMetadata() {
        List<PipeDoc> documents = new ArrayList<>();
        List<DocumentMetadata> metadataList = DocumentMetadataLoader.load99DocumentMetadata();
        
        for (int i = 0; i < metadataList.size(); i++) {
            DocumentMetadata metadata = metadataList.get(i);
            String resourcePath = "test-data/source-documents/" + metadata.getFilename();
            
            // Use the ResourceLoader to find the file in either main or test resources
            Optional<byte[]> fileDataOpt = ResourceLoader.loadResourceAsBytes(resourcePath);
            
            if (fileDataOpt.isPresent()) {
                byte[] fileData = fileDataOpt.get();
                
                // Create blob with file data
                Blob blob = Blob.newBuilder()
                        .setFilename(metadata.getFilename())
                        .setMimeType(metadata.getContentType())
                        .setData(ByteString.copyFrom(fileData))
                        .build();
                
                // Create document with full metadata
                PipeDoc document = PipeDoc.newBuilder()
                        .setId("metadata-doc-" + String.format("%03d", i))
                        .setTitle(metadata.getTitle())
                        .setBlob(blob)
                        .setSourceMimeType(metadata.getContentType())
                        .addAllKeywords(metadata.getKeywordsList())
                        .build();
                
                documents.add(document);
                LOG.debug("Created test document from metadata {}: {} ({} bytes, {})", 
                    i, metadata.getTitle(), fileData.length, metadata.getContentType());
                    
            } else {
                LOG.warn("Could not find file for metadata: {}", resourcePath);
            }
        }
        
        LOG.info("Created {} test documents from CSV metadata", documents.size());
        return documents;
    }
    
    // Other methods updated similarly...
}
```

## Gradle Configuration Changes

Update the build.gradle.kts file to properly handle test resources:

```kotlin
plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")

    implementation("org.assertj:assertj-core:3.24.2")
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Configure test resources to be included in test JAR
tasks.register<Jar>("testJar") {
    archiveClassifier.set("tests")
    from(sourceSets.test.get().output)
}

// Add the test JAR to the artifacts
artifacts {
    add("archives", tasks.named<Jar>("testJar"))
}

// Publish both main and test JARs
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            // Add the test JAR as a separate artifact
            artifact(tasks.named<Jar>("testJar"))
        }
    }
}

// Other existing configuration...
```

## Migration Strategy

1. **Phase 1: Dual Path Support**
   - Implement the ResourceLoader utility
   - Update TestDocumentLoader to use ResourceLoader
   - Keep resources in both locations temporarily
   - Run tests to verify everything still works

2. **Phase 2: Gradual Migration**
   - Move the largest files first (PDFs, etc.)
   - Update documentation to indicate the new location
   - Run tests after each batch to verify functionality

3. **Phase 3: Complete Migration**
   - Move all remaining files to test resources
   - Remove any duplicate files from main resources
   - Update build scripts to generate test JARs
   - Run full test suite to verify everything works

4. **Phase 4: Cleanup**
   - Remove any compatibility code
   - Update documentation to reflect the new structure
   - Simplify ResourceLoader if no longer needed

## Module Dependency Configuration

For modules that need access to test resources:

```kotlin
dependencies {
    // Normal dependencies
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")
    
    // For test code
    testImplementation("com.rokkon.pipeline:test-utilities:1.0.0-SNAPSHOT")
    
    // If test resources are needed
    testImplementation("com.rokkon.pipeline:test-utilities:1.0.0-SNAPSHOT:tests")
}
```

## Conclusion

This proof of concept demonstrates a practical approach to migrating test documents from main resources to test resources while maintaining compatibility and minimizing disruption. The key components are:

1. A resource loading abstraction that can find resources in either location
2. Gradual migration of files to reduce risk
3. Proper Gradle configuration to make test resources available to other modules
4. Clear documentation of the new structure and approach

By following this approach, the project can achieve the benefits of proper resource organization while minimizing the impact on development workflow.