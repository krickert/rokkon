# Rokkon Bill of Materials (BOM)

This directory contains the hierarchical Bill of Materials (BOM) structure for managing dependencies across the Rokkon project.

## Quick Reference

| BOM | Purpose | Use For |
|-----|---------|---------|
| `bom:base` | Foundation with Quarkus BOM | Parent for all other BOMs |
| `bom:library` | Shared libraries | Engine components, validators, utilities |
| `bom:cli` | Command-line tools | CLI applications (no gRPC server) |
| `bom:module` | Pipeline modules | Chunker, parser, embedder, etc. |
| `bom:server` | Full servers | Engine server, future services |

## Usage Example

```kotlin
dependencies {
    // Choose the appropriate BOM for your project type
    implementation(platform(project(":bom:module")))
    
    // Add only project-specific dependencies
    implementation("your-specific-dependency")
}
```

## Key Principles

1. **All projects must use a BOM** - No direct Quarkus BOM imports
2. **BOMs are not hierarchical in dependencies** - Each includes base + specific needs  
3. **Protobuf generation** - All projects scan `com.rokkon.pipeline:protobuf`
4. **Test dependencies** - Declare with explicit versions in projects

## Project Type Guide

### CLI Application?
- Use `bom:cli`
- For gRPC code generation: `compileOnly("io.quarkus:quarkus-grpc")`

### Pipeline Module?
- Use `bom:module`
- Includes full gRPC, REST, health, metrics support

### Engine Component/Library?
- Use `bom:library`
- For shared code without server capabilities

### Full Server Application?
- Use `bom:server`
- Complete stack with validation, OpenAPI, monitoring

## Common Migrations

```kotlin
// Old
implementation(platform(project(":rokkon-bom")))

// New - Choose based on project type
implementation(platform(project(":bom:module")))    // For modules
implementation(platform(project(":bom:library")))   // For libraries
implementation(platform(project(":bom:cli")))       // For CLIs
implementation(platform(project(":bom:server")))    // For servers
```

See [ARCHITECTURE/BOM_structure.md](../ARCHITECTURE/BOM_structure.md) for detailed documentation.