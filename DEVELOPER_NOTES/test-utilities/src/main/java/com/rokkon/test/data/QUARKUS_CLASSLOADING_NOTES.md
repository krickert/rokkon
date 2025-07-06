# Quarkus Classloading and Protobuf Classes

## The Problem
Quarkus has a very strict classloading security model that prevents normal access to protobuf-generated classes from external modules (like `commons/protobuf`). This is especially problematic in:
- Test environments
- Native compilation
- Production JARs

## The Solution (After Hours of Testing)
The ONLY reliable way to load protobuf classes in Quarkus is through explicit classloader manipulation:

```java
// !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
// Force load the class using the current thread's context classloader
// This is REQUIRED due to Quarkus classloading timing issues in tests
// Without this, the class will NOT be found and tests will fail
// DO NOT SIMPLIFY TO DIRECT CLASS REFERENCE - IT WILL NOT WORK!
ClassLoader cl = Thread.currentThread().getContextClassLoader();
Class<?> pipeDocClass = cl.loadClass("com.rokkon.search.model.PipeDoc");
// Get the parser method via reflection
var parserMethod = pipeDocClass.getMethod("parser");
var parser = (com.google.protobuf.Parser<PipeDoc>) parserMethod.invoke(null);
```

## Why Other Approaches Don't Work
1. **Direct class references**: `PipeDoc.parser()` - Class not found at runtime
2. **Quarkus index-dependency**: Helps with CDI beans but not protobuf classes
3. **ClassPathUtils**: Works for resources but not for class loading
4. **@RegisterForReflection**: Only helps with native image, not the core problem

## Critical Rules
1. **NEVER** remove the reflection-based loading
2. **NEVER** try to "simplify" to direct class references
3. **ALWAYS** use Thread.currentThread().getContextClassLoader()
4. **ALWAYS** load the class by full string name

## Test Coverage
The ProtobufTestDataHelperIT integration test verifies this approach works in:
- Dev mode
- Test mode  
- JAR mode
- (Would need additional config for native mode)

This solution was hard-won after hours of debugging. Preserve it!