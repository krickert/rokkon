/**
 * Pipelines Module - Handles pipeline creation and management
 */
const Pipelines = {
    currentPipeline: null,

    // Load and display pipelines
    async loadPipelines() {
        const pipelinesList = document.getElementById('pipelinesList');
        
        try {
            const pipelines = await API.listPipelines();
            
            if (!pipelines || pipelines.length === 0) {
                pipelinesList.innerHTML = `
                    <div class="alert alert-info">
                        <i class="bi bi-info-circle"></i> No pipelines configured yet. Create your first pipeline to get started.
                    </div>
                `;
                return;
            }
            
            let html = `
                <div class="list-group">
            `;
            
            for (const pipeline of pipelines) {
                // Validate pipeline
                const validation = await Validation.validate(pipeline);
                const validationSummary = Validation.formatValidationSummary(validation);
                const stepCount = pipeline.steps ? pipeline.steps.length : 0;
                
                html += `
                    <div class="list-group-item d-flex justify-content-between align-items-center ${!validation.valid ? 'pipeline-draft' : ''}">
                        <div>
                            <h6 class="mb-1">
                                ${pipeline.name}
                                ${!validation.valid ? `
                                    <i class="bi bi-exclamation-triangle-fill text-warning ms-2" 
                                       data-bs-toggle="popover" 
                                       data-bs-trigger="hover"
                                       data-bs-html="true"
                                       data-bs-content="${Validation.formatValidationDetails(validation).replace(/"/g, '&quot;')}"
                                       title="Validation Issues"></i>
                                ` : ''}
                            </h6>
                            <small class="text-muted">
                                Steps: ${stepCount}
                                <span class="ms-2">${validationSummary}</span>
                            </small>
                        </div>
                        <div>
                            <button class="btn btn-sm btn-outline-primary" onclick="Pipelines.editPipeline('${pipeline.name}')">
                                <i class="bi bi-pencil"></i> Edit
                            </button>
                            ${validation.valid ? `
                                <button class="btn btn-sm btn-outline-success" onclick="Pipelines.showDeployModal('${pipeline.name}')">
                                    <i class="bi bi-rocket"></i> Deploy
                                </button>
                            ` : ''}
                            <button class="btn btn-sm btn-outline-danger" onclick="Pipelines.deletePipeline('${pipeline.name}')">
                                <i class="bi bi-trash"></i> Delete
                            </button>
                        </div>
                    </div>
                `;
            }
            
            html += '</div>';
            pipelinesList.innerHTML = html;
            
            // Initialize Bootstrap popovers for validation details
            const popoverTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="popover"]'));
            popoverTriggerList.map(function (popoverTriggerEl) {
                return new bootstrap.Popover(popoverTriggerEl);
            });
        } catch (error) {
            pipelinesList.innerHTML = '<p class="text-danger">Failed to load pipelines.</p>';
            console.error('Error loading pipelines:', error);
        }
    },

    // Create new pipeline
    showCreatePipelineModal() {
        this.currentPipeline = null;
        document.getElementById('pipelineModalTitle').textContent = 'Create New Pipeline';
        document.getElementById('pipelineName').value = '';
        document.getElementById('pipelineName').disabled = false;
        document.getElementById('pipelineDescription').value = '';
        document.getElementById('pipelineSteps').innerHTML = '';
        
        const modal = new bootstrap.Modal(document.getElementById('pipelineModal'));
        modal.show();
    },

    // Edit existing pipeline
    async editPipeline(name) {
        try {
            const pipeline = await API.getPipeline(name);
            this.currentPipeline = pipeline;
            
            document.getElementById('pipelineModalTitle').textContent = 'Edit Pipeline';
            document.getElementById('pipelineName').value = pipeline.name;
            document.getElementById('pipelineName').disabled = true;
            document.getElementById('pipelineDescription').value = pipeline.description || '';
            
            // Load pipeline steps
            this.renderPipelineSteps(pipeline.steps || []);
            
            const modal = new bootstrap.Modal(document.getElementById('pipelineModal'));
            modal.show();
        } catch (error) {
            Dashboard.showError('Failed to load pipeline: ' + error.message);
        }
    },

    // Render pipeline steps in the editor
    renderPipelineSteps(steps) {
        const container = document.getElementById('pipelineSteps');
        container.innerHTML = '';
        
        steps.forEach((step, index) => {
            this.addPipelineStep(step);
        });
    },

    // Add a pipeline step
    addPipelineStep(step = null) {
        const container = document.getElementById('pipelineSteps');
        const stepIndex = container.children.length;
        
        const stepHtml = `
            <div class="pipeline-step card mb-2" data-step-index="${stepIndex}">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <h6 class="card-title">Step ${stepIndex + 1}</h6>
                        <button class="btn btn-sm btn-outline-danger" onclick="Pipelines.removeStep(${stepIndex})">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                    <div class="row">
                        <div class="col-md-6">
                            <label class="form-label">Step Name</label>
                            <input type="text" class="form-control step-name" value="${step?.name || ''}" placeholder="e.g., extract-text">
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Module</label>
                            <select class="form-select step-module">
                                <option value="">Select a module...</option>
                            </select>
                        </div>
                    </div>
                    <div class="mt-2">
                        <label class="form-label">Configuration (JSON)</label>
                        <textarea class="form-control step-config" rows="3" placeholder='{"key": "value"}'>${step?.config ? JSON.stringify(step.config, null, 2) : ''}</textarea>
                    </div>
                </div>
            </div>
        `;
        
        container.insertAdjacentHTML('beforeend', stepHtml);
        
        // Load modules for the dropdown
        this.loadModulesForStep(stepIndex, step?.module);
    },

    // Load modules for step dropdown
    async loadModulesForStep(stepIndex, selectedModule) {
        const modules = await Modules.loadRegisteredModules();
        const select = document.querySelector(`[data-step-index="${stepIndex}"] .step-module`);
        
        const options = Modules.createModuleOptions(modules, selectedModule);
        select.innerHTML = '<option value="">Select a module...</option>' + options;
    },

    // Remove a pipeline step
    removeStep(index) {
        const step = document.querySelector(`[data-step-index="${index}"]`);
        step.remove();
        
        // Renumber remaining steps
        this.renumberSteps();
    },

    // Renumber steps after removal
    renumberSteps() {
        const steps = document.querySelectorAll('.pipeline-step');
        steps.forEach((step, index) => {
            step.dataset.stepIndex = index;
            step.querySelector('.card-title').textContent = `Step ${index + 1}`;
            step.querySelector('.btn-outline-danger').onclick = () => this.removeStep(index);
        });
    },

    // Save pipeline
    async savePipeline() {
        const name = document.getElementById('pipelineName').value;
        const description = document.getElementById('pipelineDescription').value;
        
        if (!name) {
            Dashboard.showError('Pipeline name is required');
            return;
        }
        
        // Collect steps
        const steps = [];
        const stepElements = document.querySelectorAll('.pipeline-step');
        
        for (const stepEl of stepElements) {
            const stepName = stepEl.querySelector('.step-name').value;
            const module = stepEl.querySelector('.step-module').value;
            const configText = stepEl.querySelector('.step-config').value;
            
            if (!stepName || !module) {
                Dashboard.showError('All steps must have a name and module');
                return;
            }
            
            let config = {};
            if (configText) {
                try {
                    config = JSON.parse(configText);
                } catch (e) {
                    Dashboard.showError(`Invalid JSON in step "${stepName}": ${e.message}`);
                    return;
                }
            }
            
            steps.push({
                name: stepName,
                module: module,
                config: config
            });
        }
        
        const pipelineData = {
            name: name,
            description: description,
            steps: steps
        };
        
        try {
            // Validate the pipeline to show any issues
            const validation = await Validation.validate(pipelineData);
            
            // Save pipeline regardless of validation (drafts are allowed)
            let result;
            if (this.currentPipeline) {
                result = await API.updatePipeline(name, pipelineData);
            } else {
                result = await API.createPipeline(pipelineData);
            }
            
            if (result.success) {
                bootstrap.Modal.getInstance(document.getElementById('pipelineModal')).hide();
                await this.loadPipelines();
                
                // Show appropriate success message based on validation
                if (validation.valid) {
                    Dashboard.showSuccess('Pipeline saved successfully and is ready for deployment!');
                } else {
                    Dashboard.showInfo(`Pipeline saved as draft. ${validation.summary.errors} issue(s) need to be resolved before deployment.`);
                }
            } else {
                Dashboard.showError('Failed to save pipeline: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to save pipeline: ' + error.message);
        }
    },

    // Delete pipeline
    async deletePipeline(name) {
        if (!confirm(`Are you sure you want to delete the pipeline "${name}"?`)) return;
        
        try {
            const result = await API.deletePipeline(name);
            if (result.success) {
                await this.loadPipelines();
                Dashboard.showSuccess('Pipeline deleted successfully');
            } else {
                Dashboard.showError('Failed to delete pipeline: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to delete pipeline: ' + error.message);
        }
    },

    // Show deploy modal (placeholder for future implementation)
    showDeployModal(pipelineName) {
        Dashboard.showInfo(`Deploy functionality for "${pipelineName}" coming soon! This will allow you to deploy the pipeline to a specific cluster.`);
    }
};