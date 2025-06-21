package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineClusterConfig;
import com.rokkon.pipeline.config.model.PipelineGraphConfig;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineModuleMap;
import com.rokkon.pipeline.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class InterPipelineLoopValidatorTestBase {
    
    protected abstract InterPipelineLoopValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        InterPipelineLoopValidator validator = getValidator();
        assertThat(validator.getPriority()).isEqualTo(100);
        assertThat(validator.getValidatorName()).isEqualTo("InterPipelineLoopValidator");
    }
    
    @Test
    void testNullClusterConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testNullPipelineGraphConfig() {
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            null,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testEmptyPipelines() {
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Collections.emptyMap()
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Inter-pipeline loop detection is not yet implemented");
    }
    
    @Test
    void testSinglePipeline() {
        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline1", pipeline)
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Inter-pipeline loop detection is not yet implemented");
    }
    
    @Test
    void testMultiplePipelines() {
        PipelineConfig pipeline1 = new PipelineConfig(
            "pipeline1",
            Collections.emptyMap()
        );
        
        PipelineConfig pipeline2 = new PipelineConfig(
            "pipeline2",
            Collections.emptyMap()
        );
        
        PipelineConfig pipeline3 = new PipelineConfig(
            "pipeline3",
            Collections.emptyMap()
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of(
                "pipeline1", pipeline1,
                "pipeline2", pipeline2,
                "pipeline3", pipeline3
            )
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Inter-pipeline loop detection is not yet implemented");
    }
    
    // TODO: Add more tests when loop detection is implemented
    // For now, these tests just verify the validator doesn't crash
    // and returns the expected warning about incomplete implementation
}