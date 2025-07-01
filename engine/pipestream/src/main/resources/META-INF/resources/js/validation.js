/**
 * Validation Module - Comprehensive pipeline validation framework
 */
const Validation = {
    // Validation rule categories
    rules: {
        structural: {
            minSteps: {
                check: (pipeline) => (pipeline.steps?.length || 0) >= 1,
                message: 'Pipeline must have at least one step',
                severity: 'error',
                field: 'steps'
            },
            uniqueStepNames: {
                check: (pipeline) => {
                    const names = pipeline.steps?.map(s => s.name) || [];
                    return names.length === new Set(names).size;
                },
                message: 'Step names must be unique within the pipeline',
                severity: 'error',
                field: 'steps'
            },
            validStepOrder: {
                check: (pipeline) => {
                    // Future: Check for circular dependencies
                    return true;
                },
                message: 'Pipeline steps contain circular dependencies',
                severity: 'error',
                field: 'steps'
            }
        },
        
        metadata: {
            hasName: {
                check: (pipeline) => pipeline.name && pipeline.name.trim().length > 0,
                message: 'Pipeline must have a name',
                severity: 'error',
                field: 'name'
            },
            validNameFormat: {
                check: (pipeline) => /^[a-z0-9-]+$/.test(pipeline.name || ''),
                message: 'Pipeline name must contain only lowercase letters, numbers, and hyphens',
                severity: 'warning',
                field: 'name'
            },
            hasDescription: {
                check: (pipeline) => pipeline.description && pipeline.description.trim().length > 0,
                message: 'Pipeline should have a description',
                severity: 'info',
                field: 'description'
            }
        },
        
        steps: {
            hasModule: {
                check: (step) => step.module && step.module.trim().length > 0,
                message: (step) => `Step "${step.name}" must specify a module`,
                severity: 'error',
                field: 'module',
                perStep: true
            },
            hasValidConfig: {
                check: (step) => {
                    if (!step.config) return true; // Config is optional
                    try {
                        // Check if config is already an object or needs parsing
                        if (typeof step.config === 'string') {
                            JSON.parse(step.config);
                        }
                        return true;
                    } catch {
                        return false;
                    }
                },
                message: (step) => `Step "${step.name}" has invalid JSON configuration`,
                severity: 'error',
                field: 'config',
                perStep: true
            },
            moduleExists: {
                check: async (step, context) => {
                    // Check if module is registered and available
                    const modules = context.availableModules || [];
                    return modules.some(m => m.module_name === step.module);
                },
                message: (step) => `Module "${step.module}" is not registered or available`,
                severity: 'error',
                field: 'module',
                perStep: true,
                async: true
            }
        },
        
        deployment: {
            targetClusterExists: {
                check: async (pipeline, context) => {
                    if (!context.targetCluster) return true; // Not deploying yet
                    const clusters = context.availableClusters || [];
                    return clusters.some(c => c.name === context.targetCluster);
                },
                message: 'Target cluster does not exist',
                severity: 'error',
                field: 'cluster',
                async: true
            },
            resourcesAvailable: {
                check: async (pipeline, context) => {
                    // Future: Check if cluster has enough resources
                    return true;
                },
                message: 'Insufficient resources in target cluster',
                severity: 'warning',
                field: 'resources',
                async: true
            }
        }
    },

    /**
     * Validate a pipeline with specified rule sets
     * @param {Object} pipeline - The pipeline to validate
     * @param {Object} options - Validation options
     * @param {Array} options.ruleSets - Which rule sets to apply (default: ['structural', 'metadata'])
     * @param {Object} options.context - Additional context (availableModules, targetCluster, etc.)
     * @returns {Object} Validation result with detailed issues
     */
    async validate(pipeline, options = {}) {
        const ruleSets = options.ruleSets || ['structural', 'metadata'];
        const context = options.context || {};
        
        const result = {
            valid: true,
            issues: [],
            summary: {
                errors: 0,
                warnings: 0,
                info: 0
            }
        };

        // Run validation rules
        for (const ruleSetName of ruleSets) {
            const ruleSet = this.rules[ruleSetName];
            if (!ruleSet) continue;

            for (const [ruleName, rule] of Object.entries(ruleSet)) {
                try {
                    if (rule.perStep && pipeline.steps) {
                        // Validate each step
                        for (let i = 0; i < pipeline.steps.length; i++) {
                            const step = pipeline.steps[i];
                            const passed = rule.async ? 
                                await rule.check(step, context) : 
                                rule.check(step, context);
                                
                            if (!passed) {
                                const issue = {
                                    ruleSet: ruleSetName,
                                    rule: ruleName,
                                    severity: rule.severity,
                                    message: typeof rule.message === 'function' ? 
                                        rule.message(step) : rule.message,
                                    field: rule.field,
                                    stepIndex: i,
                                    stepName: step.name
                                };
                                result.issues.push(issue);
                                result.summary[rule.severity + 's']++;
                                if (rule.severity === 'error') {
                                    result.valid = false;
                                }
                            }
                        }
                    } else {
                        // Validate pipeline level
                        const passed = rule.async ? 
                            await rule.check(pipeline, context) : 
                            rule.check(pipeline, context);
                            
                        if (!passed) {
                            const issue = {
                                ruleSet: ruleSetName,
                                rule: ruleName,
                                severity: rule.severity,
                                message: typeof rule.message === 'function' ? 
                                    rule.message(pipeline) : rule.message,
                                field: rule.field
                            };
                            result.issues.push(issue);
                            result.summary[rule.severity + 's']++;
                            if (rule.severity === 'error') {
                                result.valid = false;
                            }
                        }
                    }
                } catch (error) {
                    console.error(`Validation rule ${ruleName} failed:`, error);
                    result.issues.push({
                        ruleSet: ruleSetName,
                        rule: ruleName,
                        severity: 'error',
                        message: `Validation rule error: ${error.message}`,
                        field: 'system'
                    });
                    result.summary.errors++;
                    result.valid = false;
                }
            }
        }

        // Add validation timestamp
        result.timestamp = new Date().toISOString();
        result.pipelineName = pipeline.name;

        return result;
    },

    /**
     * Format validation result for display
     * @param {Object} validationResult - Result from validate()
     * @returns {String} HTML formatted validation summary
     */
    formatValidationSummary(validationResult) {
        if (validationResult.valid && validationResult.issues.length === 0) {
            return '<span class="text-success"><i class="bi bi-check-circle"></i> Ready for deployment</span>';
        }

        let html = '<div class="validation-summary">';
        
        // Summary badges
        if (validationResult.summary.errors > 0) {
            html += `<span class="badge bg-danger me-1">${validationResult.summary.errors} errors</span>`;
        }
        if (validationResult.summary.warnings > 0) {
            html += `<span class="badge bg-warning me-1">${validationResult.summary.warnings} warnings</span>`;
        }
        if (validationResult.summary.info > 0) {
            html += `<span class="badge bg-info me-1">${validationResult.summary.info} info</span>`;
        }

        html += '</div>';
        return html;
    },

    /**
     * Format detailed validation issues
     * @param {Object} validationResult - Result from validate()
     * @returns {String} HTML formatted issue list
     */
    formatValidationDetails(validationResult) {
        if (validationResult.issues.length === 0) {
            return '<p class="text-success">No issues found.</p>';
        }

        let html = '<div class="validation-details">';
        
        // Group by severity
        const grouped = {
            error: validationResult.issues.filter(i => i.severity === 'error'),
            warning: validationResult.issues.filter(i => i.severity === 'warning'),
            info: validationResult.issues.filter(i => i.severity === 'info')
        };

        for (const [severity, issues] of Object.entries(grouped)) {
            if (issues.length === 0) continue;

            const iconClass = {
                error: 'bi-x-circle-fill text-danger',
                warning: 'bi-exclamation-triangle-fill text-warning',
                info: 'bi-info-circle-fill text-info'
            }[severity];

            html += `<div class="mb-3">`;
            html += `<h6 class="text-${severity === 'error' ? 'danger' : severity}">${severity.charAt(0).toUpperCase() + severity.slice(1)}s</h6>`;
            html += '<ul class="list-unstyled">';
            
            for (const issue of issues) {
                html += `<li class="mb-1">`;
                html += `<i class="bi ${iconClass} me-2"></i>`;
                html += issue.message;
                if (issue.stepName) {
                    html += ` <small class="text-muted">(Step: ${issue.stepName})</small>`;
                }
                html += '</li>';
            }
            
            html += '</ul></div>';
        }

        html += '</div>';
        return html;
    },

    /**
     * Quick check if pipeline is deployable (no errors)
     * @param {Object} pipeline - The pipeline to check
     * @returns {Boolean} True if deployable
     */
    async isDeployable(pipeline) {
        const result = await this.validate(pipeline, {
            ruleSets: ['structural', 'metadata']
        });
        return result.valid;
    },

    /**
     * Full deployment validation including async checks
     * @param {Object} pipeline - The pipeline to validate
     * @param {String} targetCluster - Target cluster name
     * @param {Array} availableModules - List of available modules
     * @returns {Object} Full validation result
     */
    async validateForDeployment(pipeline, targetCluster, availableModules) {
        return await this.validate(pipeline, {
            ruleSets: ['structural', 'metadata', 'steps', 'deployment'],
            context: {
                targetCluster,
                availableModules,
                availableClusters: await API.listClusters()
            }
        });
    }
};