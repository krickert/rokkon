package com.rokkon.engine.api;

import java.util.List;

// Note: PipelineStepRequestDTO should be in its own file or defined elsewhere
// if this file only contains PipelineDefinitionRequestDTO.
// For this tool, I'll assume PipelineStepRequestDTO is recognized from another created file.
// If creating sequentially, PipelineStepRequestDTO.java should be created first or use fully qualified name.
// However, the prompt implied creating both in this step. I will define them separately.

public record PipelineDefinitionRequestDTO(
    String name,
    String description,
    List<PipelineStepRequestDTO> steps
) {}
