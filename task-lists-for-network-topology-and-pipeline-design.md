# Task Lists for Updating Network_topology.md and Pipeline_design.md

## Task List for Network_topology.md

### Regular Text Replacements
1. Line 1: Replace title "Rokkon Engine: Network Topology" with "Pipeline Engine: Network Topology"
2. Line 3: Replace "The Rokkon Engine and its ecosystem" with "The Pipeline Engine and its ecosystem"
3. Line 9: Replace "**Rokkon Engine:**" with "**Pipeline Engine:**"
4. Line 18: Replace "from the Rokkon Engine" with "from the Pipeline Engine"
5. Line 19: Replace "Connect to the Rokkon Engine's" with "Connect to the Pipeline Engine's"
6. Line 19: Replace "via `rokkon-cli`" with "via `pipeline-cli`"
7. Line 25: Replace "from the Rokkon Engine" with "from the Pipeline Engine"
8. Line 30: Replace "from the Rokkon Engine" with "from the Pipeline Engine"
9. Line 34: Replace "Connect to the Rokkon Engine's" with "Connect to the Pipeline Engine's"
10. Line 40: Replace "All components (Rokkon Engine" with "All components (Pipeline Engine"
11. Line 134: Replace "Rokkon Engine might run" with "Pipeline Engine might run"
12. Line 214: Replace "Rokkon Engine UI/API" with "Pipeline Engine UI/API"
13. Line 222: Replace "The Rokkon Engine's design" with "The Pipeline Engine's design"

### Mermaid Diagram 1 (Single-Host Setup) - Lines 44-75
1. Line 47: Replace `RokkonEngine[Rokkon Engine <br> HTTP:38090, gRPC:49000]` with `PipelineEngine[Pipeline Engine <br> HTTP:38090, gRPC:49000]`
2. Lines 53-56: Replace all occurrences of `RokkonEngine` with `PipelineEngine`
3. Lines 58-60: Replace all occurrences of `RokkonEngine` with `PipelineEngine`
4. Line 69: Replace `RokkonEngine` with `PipelineEngine`
5. Line 73: Replace `class RokkonEngine` with `class PipelineEngine`

### Mermaid Diagram 2 (Kubernetes Deployment) - Lines 86-130
1. Line 91: Replace `Namespace: rokkon` with `Namespace: pipeline`
2. Line 92: Replace `RokkonEngineService[K8s Service: rokkon-engine]` with `PipelineEngineService[K8s Service: pipeline-engine]`
3. Line 92: Replace `RokkonEnginePods[Rokkon Engine Pods <br> (Deployment)]` with `PipelineEnginePods[Pipeline Engine Pods <br> (Deployment)]`
4. Lines 100-101: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
5. Lines 104-105: Replace `K8s DNS: rokkon-engine.rokkon...` with `K8s DNS: pipeline-engine.pipeline...`
6. Lines 104-105: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
7. Lines 108-109: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
8. Line 118: Replace `RokkonEngineService` with `PipelineEngineService`
9. Line 127: Replace `class RokkonEngineService` with `class PipelineEngineService`
10. Line 128: Replace `class RokkonEnginePods` with `class PipelineEnginePods`

### Mermaid Diagram 3 (Multi-Cluster Deployment) - Lines 144-188
1. Line 148: Replace `Namespace: rokkon` with `Namespace: pipeline`
2. Line 149: Replace `RokkonEngineService[K8s Service: rokkon-engine]` with `PipelineEngineService[K8s Service: pipeline-engine]`
3. Line 149: Replace `RokkonEnginePods["Rokkon Engine Pods <br> (Deployment)"]` with `PipelineEnginePods["Pipeline Engine Pods <br> (Deployment)"]`
4. Lines 157-158: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
5. Lines 161-162: Replace `K8s DNS: rokkon-engine.rokkon...` with `K8s DNS: pipeline-engine.pipeline...`
6. Lines 161-162: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
7. Lines 165-166: Replace all occurrences of `RokkonEnginePods` with `PipelineEnginePods`
8. Line 175: Replace `RokkonEngineService` with `PipelineEngineService`
9. Line 184: Replace `class RokkonEngineService` with `class PipelineEngineService`
10. Line 185: Replace `class RokkonEnginePods` with `class PipelineEnginePods`

## Task List for Pipeline_design.md

### Regular Text Replacements
1. Line 1: Replace title "Rokkon Engine: Pipeline Design" with "Pipeline Engine: Pipeline Design"
2. Line 5: Replace "The Rokkon Engine organizes" with "The Pipeline Engine organizes"
3. Line 53: Replace "`rokkon/<cluster_name>/cluster-config`" with "`pipeline/<cluster_name>/cluster-config`"
4. Line 65: Replace "`rokkon/<cluster_name>/pipelines/<pipeline_id>`" with "`pipeline/<cluster_name>/pipelines/<pipeline_id>`"
5. Line 85: Replace "The Rokkon Engine discovers" with "The Pipeline Engine discovers"
6. Line 90: Replace "A cornerstone of the Rokkon Engine's flexibility" with "A cornerstone of the Pipeline Engine's flexibility"
7. Line 93: Replace "`rokkon/default_cluster/pipelines/my_document_pipeline`" with "`pipeline/default_cluster/pipelines/my_document_pipeline`"
8. Line 95: Replace "The Rokkon Engine (and potentially individual modules)" with "The Pipeline Engine (and potentially individual modules)"
9. Line 137: Replace "defined by Rokkon's Protocol Buffers" with "defined by Pipeline's Protocol Buffers"
10. Line 137: Replace "integrated into the Rokkon Engine" with "integrated into the Pipeline Engine"
11. Line 242: Replace "Registration with Rokkon Engine" with "Registration with Pipeline Engine"
12. Line 243: Replace "`rokkon-cli register`" with "`pipeline-cli register`"
13. Line 259: Replace "package com.rokkon.modules.embedder" with "package com.rokkon.modules.embedder" (keep this as is - package name stays)
14. Line 261-263: Keep all "com.rokkon.proto" references as they are (package names stay)
15. Line 322: Replace "This allows the Rokkon Engine" with "This allows the Pipeline Engine"
16. Line 328: Replace "Rokkon allows developers" with "Pipeline allows developers"

### Mermaid Diagram 1 (Logical Design Hierarchy) - Lines 7-47
No changes needed in this diagram as it doesn't contain "Rokkon" references.

### Mermaid Diagram 2 (Dynamic Configuration) - Lines 105-133
1. Line 109: Replace `RokkonCLI as Rokkon CLI/UI` with `PipelineCLI as Pipeline CLI/UI`
2. Line 112: Replace `RokkonEngine as Rokkon Engine` with `PipelineEngine as Pipeline Engine`
3. Line 115: Replace `RokkonCLI` with `PipelineCLI`
4. Line 122: Replace `RokkonCLI` with `PipelineCLI`
5. Line 124: Replace `Note over ConsulKV, RokkonEngine: Rokkon Engine is watching 'rokkon/cluster/pipelines/PipelineX'` with `Note over ConsulKV, PipelineEngine: Pipeline Engine is watching 'pipeline/cluster/pipelines/PipelineX'`
6. Line 125: Replace `ConsulKV ->> RokkonEngine` with `ConsulKV ->> PipelineEngine`
7. Line 126: Replace `RokkonEngine ->> RokkonEngine` with `PipelineEngine ->> PipelineEngine`
8. Line 127: Replace `RokkonEngine ->> RokkonEngine` with `PipelineEngine ->> PipelineEngine`
9. Line 130: Replace `RokkonEngine ->> ModuleA` with `PipelineEngine ->> ModuleA`

### Code Examples
1. Python Example (Lines 189-253):
   - Line 243: Replace "`rokkon-cli register`" with "`pipeline-cli register`"

2. Java Example (Lines 257-318):
   - Keep package names with "rokkon" as they are (com.rokkon.*)