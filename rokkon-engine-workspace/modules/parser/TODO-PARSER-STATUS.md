# PARSER MODULE - STATUS AND TODO

## ⚠️ IMPORTANT: MODULE NOT READY FOR BUILD

This parser module is **NOT included in the parent build** and should **NOT be activated** until the prerequisite infrastructure is in place.

## WHERE WE LEFT OFF

### ✅ COMPLETED WORK
1. **Module Structure**: Created using Quarkus CLI following exact echo pattern
2. **Dependencies**: Added Tika and JSON schema validation dependencies
3. **Business Logic Migration**: 
   - `DocumentParser.java` - Core Tika parsing logic (sophisticated, preserves all business rules)
   - `MetadataMapper.java` - Metadata processing utility (configurable value limits)
   - `ParserServiceImpl.java` - gRPC service implementation with real parsing
4. **Configuration Schema**: Created `parser-config-schema.json` with comprehensive options
5. **Test Structure**: Both unit (@QuarkusTest) and integration (@QuarkusIntegrationTest) tests

### ❌ ARCHITECTURAL MISTAKE MADE
- **Created decentralized configuration** (`ParserConfiguration.java`) 
- **This violates the established pattern** - configuration should be centralized
- **Original tika implementation was RIGHT** - just extract config from ProcessRequest

## 🚨 CRITICAL DEPENDENCIES MISSING

### 1. CONSUL CONFIGURATION & API (Priority 1)
**Location**: `backup-refactor/src/main/java/com/rokkon/search/config/pipeline/`
**What to migrate**:
- `service/PipelineConfigService.java` - Consul-based config management
- `service/SchemaValidationService.java` - JSON schema validation
- `api/PipelineConfigResource.java` - REST API for config management
- `api/ValidationResource.java` - Validation endpoints

### 2. COMMONS/VALIDATION INFRASTRUCTURE (Priority 2)  
**Location**: `backup-refactor/src/main/java/com/rokkon/search/config/pipeline/model/`
**What to migrate**:
- `PipelineModuleConfiguration.java` - Module config model
- `SchemaReference.java` - Schema reference model
- `service/validation/` - All validation rules and infrastructure
- `ValidationResult.java` - Validation result structure

### 3. ENGINE CORE (Priority 3)
**Location**: `backup-refactor/src/main/java/com/rokkon/search/engine/registration/`
**What to migrate**:
- `ModuleRegistrationService.java` - Module registration with validation
- `ConsulModuleRegistry.java` - Consul-based module registry
- `DynamicGrpcClientManager.java` - gRPC client management

## CORRECT ARCHITECTURE PATTERN

### Parser Module Should Be THIN:
```java
@GrpcService
public class ParserServiceImpl implements PipeStepProcessor {
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Extract config directly from request (NO custom config class)
        Map<String, String> config = request.getConfig().getConfigParamsMap();
        
        // Call business logic utility
        PipeDoc result = DocumentParser.parseDocument(content, config, filename);
        
        // Return result
    }
}
```

### Configuration System Should Be CENTRALIZED:
- **Schema validation** in commons/validation
- **Consul integration** in engine-config  
- **Module registration** in engine-core
- **Parser just consumes** standard ProcessRequest.config

## WHAT TO DO NEXT

### Phase 1: Consul Configuration (FIRST)
1. Create `engine-config` project
2. Migrate `PipelineConfigService` for Consul integration
3. Migrate `SchemaValidationService` for JSON schema validation
4. Create REST API for configuration management

### Phase 2: Commons Validation (SECOND) 
1. Create `commons` project 
2. Migrate all validation infrastructure
3. Migrate configuration models (`PipelineModuleConfiguration`, etc.)
4. Test validation rules work correctly

### Phase 3: Engine Core (THIRD)
1. Create `engine-core` project
2. Migrate module registration service
3. Migrate gRPC client management
4. Test end-to-end module registration

### Phase 4: Parser Finalization (LAST)
1. **Remove** `ParserConfiguration.java` (decentralized mistake)
2. **Simplify** `ParserServiceImpl` to use standard config pattern
3. **Keep** `DocumentParser.java` business logic (it's solid)
4. **Test** with centralized configuration system
5. **Add to parent build** only after dependencies are ready

## KEY LESSONS LEARNED

1. **Follow the original tika pattern** - it was architecturally correct
2. **Don't create decentralized config** - use the centralized system
3. **Build dependencies first** - commons → engine-config → engine-core → modules
4. **The backup-refactor code was RIGHT** - follow those patterns

## FILES TO REVIEW WHEN READY

### Original Tika Implementation (Reference):
- `backup-hosed-modules/tika-parser-backup-20250616-143138/src/main/java/com/rokkon/modules/tika/TikaService.java`
- Shows correct config extraction pattern

### Configuration System (To migrate first):
- `backup-refactor/src/main/java/com/rokkon/search/config/pipeline/`
- This is the RIGHT way to do configuration

### Testing Infrastructure (To preserve):
- Current test structure in parser module is CORRECT (abstract base pattern)
- Keep this when we finalize the parser

## CURRENT STATUS: BLOCKED ON DEPENDENCIES
**Do not activate this module until consul-config, commons validation, and engine-core are migrated and working.**