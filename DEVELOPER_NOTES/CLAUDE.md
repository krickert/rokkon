# Rokkon Engine Development Guide

## Project Status (Updated: June 2025)

### Current State
The Rokkon Engine project has reached a significant milestone with core functionality operational:

#### ✅ Completed
1. **Test Infrastructure** - Nearly all tests passing across modules
   - Proper Consul integration with Testcontainers
   - Event-driven architecture for module registration
   - Comprehensive test utilities and base classes

2. **Engine Startup** - Engine starts correctly in dev mode
   - gRPC services properly discovered and registered
   - REST API endpoints migrated from consul to engine module
   - Consul configuration management working

3. **Frontend Dashboard** - Modern, modular UI implementation
   - Migrated from 939-line monolithic HTML to modular architecture
   - Separate JS modules for API, services, modules, pipelines, clusters
   - Two-tier validation framework (lenient creation, strict deployment)
   - Real-time service discovery and health monitoring
   - Snake_case JSON property support for data scientist compatibility

4. **Container Deployment** - Enhanced container management
   - Docker-based module deployment
   - Automatic zombie instance cleanup
   - Health check monitoring
   - Multi-instance support for same module type

#### 🚧 In Progress
1. **Frontend Enhancements**
   - Pipeline builder with drag-and-drop support
   - Advanced validation feedback with detailed error messages
   - Deployment workflow UI
   - Real-time metrics dashboard

2. **Container Deployment Improvements**
   - Kubernetes support
   - Auto-scaling based on load
   - Resource allocation management
   - Container versioning and rollback

#### 🔜 Next Phase
1. **gRPC Engine-to-Engine Communication**
   - Multi-cluster pipeline execution
   - Distributed processing coordination
   - Cross-engine module discovery
   - Federated authentication

2. **Production Readiness**
   - Performance optimization
   - Security hardening
   - Monitoring and observability
   - Documentation completion

## Testing Strategy (CRITICAL)

### Core Testing Rules
- Do not use mockito directy in quarkus, always use the quarkus version of injectmock instead

## Recent Architecture Improvements

### Frontend Modularization (June 2025)
The dashboard has been completely refactored from a monolithic 939-line HTML file to a clean modular structure:

```
/META-INF/resources/
├── index.html (268 lines - core structure only)
├── css/
│   └── dashboard.css (208 lines - all styles)
└── js/
    ├── api.js (134 lines - centralized API calls)
    ├── validation.js (315 lines - comprehensive validation framework)
    ├── services.js (197 lines - service rendering)
    ├── modules.js (119 lines - module management)
    ├── pipelines.js (282 lines - pipeline management)
    ├── clusters.js (113 lines - cluster management)
    └── main.js (195 lines - coordination/initialization)
```

**Benefits:**
- Each file has single responsibility
- Easy to maintain and extend
- Better browser caching
- Enables unit testing
- Multiple developers can work in parallel

### Two-Tier Validation Framework
Implemented a sophisticated validation system that differentiates between creation and deployment:

1. **Creation Validation (Lenient)**
   - Allows saving drafts and work-in-progress
   - No blocking errors for incomplete pipelines
   - Enables iterative development

2. **Deployment Validation (Strict)**
   - Comprehensive checks before deployment
   - Module availability verification
   - Resource requirement validation
   - Dependency resolution

**Validation Categories:**
- **Structural**: Pipeline structure (steps, dependencies)
- **Metadata**: Names, descriptions, tags
- **Steps**: Individual step validation
- **Deployment**: Cluster and resource checks

### Snake_Case JSON Convention
All JSON properties now use snake_case to align with data scientist preferences:
- `moduleName` → `module_name`
- `createdAt` → `created_at`
- `baseServices` → `base_services`

This is enforced by the `JsonOrderingCustomizer` in `rokkon-commons`.

### Event-Driven Module Registration
Module registration now uses CDI events to avoid circular dependencies:
1. gRPC service receives registration request
2. Fires `ModuleRegistrationRequestEvent`
3. `GlobalModuleRegistryService` processes registration
4. Fires `ModuleRegistrationResponseEvent`
5. gRPC service completes response

This clean separation allows the registration module to remain independent of the consul module. 

[Rest of the existing content remains unchanged...]