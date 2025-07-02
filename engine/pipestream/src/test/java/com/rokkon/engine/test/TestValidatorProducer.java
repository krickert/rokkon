package com.rokkon.engine.test;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineClusterConfig;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.CompositeValidatorBuilder;
import com.rokkon.pipeline.validation.ConfigValidator;
import com.rokkon.pipeline.validation.ValidationMode;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.Mock;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test-specific validator producer that can be configured to use
 * real validators, mocks, or a mix of both.
 */
@Mock
@ApplicationScoped
@Alternative
@Priority(1)
public class TestValidatorProducer {
    
    private static final Logger LOG = Logger.getLogger(TestValidatorProducer.class);
    
    @ConfigProperty(name = "test.validators.use-real", defaultValue = "false")
    boolean useRealValidators;
    
    @ConfigProperty(name = "test.validators.mode", defaultValue = "empty")
    String validatorMode; // empty, real, failing, mixed
    
    // Don't inject real validators - the @Mock annotation prevents the real producer from running
    
    /**
     * Produces a test-friendly CompositeValidator for PipelineConfig.
     */
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfig> testPipelineConfigValidator() {
        CompositeValidatorBuilder<PipelineConfig> builder = CompositeValidatorBuilder.<PipelineConfig>create()
            .withName("TestPipelineConfigValidator");
        
        switch (validatorMode) {
            case "real":
                // Create a test validator that mimics ProcessorInfoValidator behavior
                builder.addValidator(new ConfigValidator<PipelineConfig>() {
                    @Override
                    public ValidationResult validate(PipelineConfig config) {
                        List<String> warnings = new ArrayList<>();
                        
                        if (config.pipelineSteps() != null) {
                            for (var entry : config.pipelineSteps().entrySet()) {
                                var step = entry.getValue();
                                if (step != null && step.processorInfo() != null) {
                                    String serviceName = step.processorInfo().grpcServiceName();
                                    if (serviceName != null && (serviceName.contains("localhost") || serviceName.contains("127.0.0.1"))) {
                                        warnings.add("Step '" + entry.getKey() + "': gRPC service name contains localhost reference - ensure this is intentional");
                                    }
                                }
                            }
                        }
                        
                        return warnings.isEmpty() ? ValidationResultFactory.success() : 
                            ValidationResultFactory.successWithWarnings(warnings);
                    }
                    
                    @Override
                    public String getValidatorName() {
                        return "TestProcessorInfoValidator";
                    }
                    
                    @Override
                    public Set<ValidationMode> supportedModes() {
                        return Set.of(ValidationMode.PRODUCTION);
                    }
                });
                LOG.info("Using REAL validators for PipelineConfig");
                break;
                
            case "failing":
                // Always fail for testing error paths
                builder.withFailingValidation("Test validation failure");
                LOG.info("Using FAILING validator for PipelineConfig");
                break;
                
            case "mixed":
                // Mix of real and test validators
                builder.addValidator(new ConfigValidator<PipelineConfig>() {
                    @Override
                    public ValidationResult validate(PipelineConfig config) {
                        // Simple test validation
                        if (config.name() == null || config.name().isEmpty()) {
                            return ValidationResultFactory.failure("Pipeline name is required");
                        }
                        return ValidationResult.empty();
                    }
                    
                    @Override
                    public String getValidatorName() {
                        return "TestNameValidator";
                    }
                });
                LOG.info("Using MIXED validators for PipelineConfig");
                break;
                
            case "empty":
            default:
                // No validation - always succeeds
                builder.withEmptyValidation();
                LOG.info("Using EMPTY validator for PipelineConfig");
                break;
        }
        
        return builder.build();
    }
    
    /**
     * Produces a test-friendly CompositeValidator for PipelineClusterConfig.
     */
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineClusterConfig> testClusterConfigValidator() {
        CompositeValidatorBuilder<PipelineClusterConfig> builder = CompositeValidatorBuilder.<PipelineClusterConfig>create()
            .withName("TestPipelineClusterConfigValidator");
        
        switch (validatorMode) {
            case "real":
                // Manually create the real validators since @Mock prevents real producer from running
                // Add cluster validators here if needed
                LOG.info("Using REAL validators for PipelineClusterConfig");
                break;
                
            case "failing":
                builder.withFailingValidation("Test cluster validation failure");
                LOG.info("Using FAILING validator for PipelineClusterConfig");
                break;
                
            case "empty":
            default:
                builder.withEmptyValidation();
                LOG.info("Using EMPTY validator for PipelineClusterConfig");
                break;
        }
        
        return builder.build();
    }
}