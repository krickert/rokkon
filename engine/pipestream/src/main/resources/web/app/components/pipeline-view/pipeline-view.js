import { LitElement, html, css } from 'lit';
import '../pipeline-builder/pipeline-builder.js';

export class PipelineView extends LitElement {
  static properties = {
    pipelines: { type: Array },
    loading: { type: Boolean },
    error: { type: String },
    showModal: { type: Boolean },
    editingPipeline: { type: Object }
  };

  static styles = css`
    :host {
      display: block;
    }

    .pipeline-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .section-title {
      font-size: 20px;
      font-weight: 600;
      color: #333;
    }

    .create-button {
      background: #1976d2;
      color: white;
      border: none;
      padding: 10px 20px;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }

    .create-button:hover {
      background: #1565c0;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }

    .pipeline-list {
      display: grid;
      gap: 16px;
    }

    .pipeline-card {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 20px;
      transition: all 0.2s ease;
    }

    .pipeline-card:hover {
      border-color: #1976d2;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }

    .pipeline-card.draft {
      border-left: 4px solid #ff9800;
    }

    .pipeline-card.invalid {
      border-left: 4px solid #f44336;
    }

    .pipeline-card.valid {
      border-left: 4px solid #4caf50;
    }

    .pipeline-info {
      display: flex;
      justify-content: space-between;
      align-items: start;
      margin-bottom: 12px;
    }

    .pipeline-name {
      font-size: 18px;
      font-weight: 600;
      color: #333;
      margin-bottom: 4px;
    }

    .pipeline-meta {
      display: flex;
      gap: 12px;
      font-size: 14px;
      color: #666;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .pipeline-actions {
      display: flex;
      gap: 8px;
    }

    .action-button {
      padding: 6px 12px;
      border: 1px solid #e0e0e0;
      background: white;
      border-radius: 4px;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .action-button:hover {
      background: #f5f5f5;
      border-color: #1976d2;
    }

    .action-button.deploy {
      background: #4caf50;
      color: white;
      border-color: #4caf50;
    }

    .action-button.deploy:hover {
      background: #45a049;
    }

    .action-button.delete {
      color: #f44336;
      border-color: #f44336;
    }

    .action-button.delete:hover {
      background: #ffebee;
    }

    .validation-status {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 8px;
      padding: 8px;
      border-radius: 4px;
      font-size: 13px;
    }

    .validation-status.valid {
      background: #e8f5e9;
      color: #2e7d32;
    }

    .validation-status.invalid {
      background: #ffebee;
      color: #c62828;
    }

    .validation-status.draft {
      background: #fff3cd;
      color: #856404;
    }

    .status-icon {
      width: 16px;
      height: 16px;
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #999;
    }

    .empty-icon {
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
      opacity: 0.3;
    }

    .loading {
      text-align: center;
      padding: 40px;
      color: #666;
    }

    .error {
      background: #fee;
      border: 1px solid #fcc;
      border-radius: 4px;
      padding: 16px;
      margin-bottom: 20px;
      color: #c00;
    }

    .plus-icon {
      width: 20px;
      height: 20px;
    }
  `;

  constructor() {
    super();
    this.pipelines = [];
    this.loading = true;
    this.error = null;
    this.showModal = false;
    this.editingPipeline = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this.fetchPipelines();
  }

  async fetchPipelines() {
    try {
      this.loading = true;
      const response = await fetch('/api/v1/pipelines/definitions');
      if (!response.ok) throw new Error('Failed to fetch pipelines');
      
      this.pipelines = await response.json();
      this.loading = false;
    } catch (err) {
      this.error = err.message;
      this.loading = false;
    }
  }

  refresh() {
    this.fetchPipelines();
  }

  createPipeline() {
    this.editingPipeline = null;
    this.showModal = true;
  }

  editPipeline(pipeline) {
    this.editingPipeline = pipeline;
    this.showModal = true;
  }

  async deployPipeline(pipeline) {
    try {
      const response = await fetch(`/api/v1/pipelines/definitions/${pipeline.id}/deploy`, {
        method: 'POST'
      });
      if (!response.ok) throw new Error('Failed to deploy pipeline');
      
      this.showToast('Pipeline deployed successfully', 'success');
      this.fetchPipelines();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  async deletePipeline(pipeline) {
    if (!confirm(`Are you sure you want to delete pipeline "${pipeline.name}"?`)) {
      return;
    }

    try {
      const response = await fetch(`/api/v1/pipelines/definitions/${pipeline.id}`, {
        method: 'DELETE'
      });
      if (!response.ok) throw new Error('Failed to delete pipeline');
      
      this.showToast('Pipeline deleted successfully', 'success');
      this.fetchPipelines();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  closeModal() {
    this.showModal = false;
    this.editingPipeline = null;
  }

  async savePipeline(pipeline) {
    try {
      // When editing, use the edit mode (DESIGN validation)
      // For new pipelines, start with DESIGN mode for easier creation
      const validationMode = 'DESIGN';
      
      if (this.editingPipeline) {
        // Update existing pipeline
        const response = await fetch(`/api/v1/pipelines/definitions/${this.editingPipeline.id}?validationMode=${validationMode}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(this.editingPipeline) // Use full pipeline config, not just DTO
        });
        
        if (!response.ok) {
          const error = await response.json();
          throw new Error(error.message || 'Failed to update pipeline');
        }
        
        const result = await response.json();
        if (result.warnings && result.warnings.length > 0) {
          this.showToast(`Pipeline updated with warnings: ${result.warnings.join(', ')}`, 'warning');
        } else {
          this.showToast('Pipeline updated successfully', 'success');
        }
      } else {
        // Create new pipeline using the DTO endpoint
        const response = await fetch(`/api/v1/pipelines/definitions?validationMode=${validationMode}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(pipeline)
        });
        
        if (!response.ok) {
          const error = await response.json();
          throw new Error(error.message || 'Failed to create pipeline');
        }
        
        const result = await response.json();
        if (result.warnings && result.warnings.length > 0) {
          this.showToast(`Pipeline created with warnings: ${result.warnings.join(', ')}`, 'warning');
        } else {
          this.showToast('Pipeline created successfully', 'success');
        }
      }
      
      this.closeModal();
      this.fetchPipelines();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  handleValidation(validationResult) {
    // Update the pipeline validation status in the modal
    // This could be used to enable/disable save button or show warnings
    console.log('Pipeline validation:', validationResult);
  }

  showToast(message, type) {
    const event = new CustomEvent('show-toast', {
      detail: { message, type },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  getValidationIcon(status) {
    if (status === 'valid') {
      return html`<svg class="status-icon" fill="currentColor" viewBox="0 0 20 20">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
      </svg>`;
    } else if (status === 'invalid') {
      return html`<svg class="status-icon" fill="currentColor" viewBox="0 0 20 20">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
      </svg>`;
    } else {
      return html`<svg class="status-icon" fill="currentColor" viewBox="0 0 20 20">
        <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
      </svg>`;
    }
  }

  render() {
    if (this.loading) {
      return html`<div class="loading">Loading pipelines...</div>`;
    }

    if (this.error) {
      return html`<div class="error">Error: ${this.error}</div>`;
    }

    return html`
      <div class="pipeline-header">
        <h2 class="section-title">Pipelines</h2>
        <button class="create-button" @click=${this.createPipeline}>
          <svg class="plus-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
          </svg>
          Create Pipeline
        </button>
      </div>

      ${this.pipelines.length === 0 ? html`
        <div class="empty-state">
          <svg class="empty-icon" fill="currentColor" viewBox="0 0 20 20">
            <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
          </svg>
          <p>No pipelines found</p>
          <p>Create your first pipeline to get started</p>
        </div>
      ` : html`
        <div class="pipeline-list">
          ${this.pipelines.map(pipeline => html`
            <div class="pipeline-card ${pipeline.validationStatus || 'draft'}">
              <div class="pipeline-info">
                <div>
                  <div class="pipeline-name">${pipeline.name}</div>
                  <div class="pipeline-meta">
                    <span class="meta-item">
                      <svg width="14" height="14" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z"/>
                        <path fill-rule="evenodd" d="M4 5a2 2 0 012-2 1 1 0 000 2H6a2 2 0 00-2 2v6a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-1a1 1 0 100-2 2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2V5z" clip-rule="evenodd"/>
                      </svg>
                      ${pipeline.steps?.length || 0} steps
                    </span>
                    ${pipeline.cluster ? html`
                      <span class="meta-item">
                        <svg width="14" height="14" fill="currentColor" viewBox="0 0 20 20">
                          <path d="M13 7H7v6h6V7z"/>
                          <path fill-rule="evenodd" d="M7 2a1 1 0 012 0v1h2V2a1 1 0 112 0v1h2a2 2 0 012 2v2h1a1 1 0 110 2h-1v2h1a1 1 0 110 2h-1v2a2 2 0 01-2 2h-2v1a1 1 0 11-2 0v-1H9v1a1 1 0 11-2 0v-1H5a2 2 0 01-2-2v-2H2a1 1 0 110-2h1V9H2a1 1 0 010-2h1V5a2 2 0 012-2h2V2z" clip-rule="evenodd"/>
                        </svg>
                        ${pipeline.cluster}
                      </span>
                    ` : ''}
                  </div>
                </div>
                <div class="pipeline-actions">
                  <button class="action-button" @click=${() => this.editPipeline(pipeline)}>
                    Edit
                  </button>
                  ${pipeline.validationStatus === 'valid' ? html`
                    <button class="action-button deploy" @click=${() => this.deployPipeline(pipeline)}>
                      Deploy
                    </button>
                  ` : ''}
                  <button class="action-button delete" @click=${() => this.deletePipeline(pipeline)}>
                    Delete
                  </button>
                </div>
              </div>
              
              <div class="validation-status ${pipeline.validationStatus || 'draft'}">
                ${this.getValidationIcon(pipeline.validationStatus)}
                ${pipeline.validationStatus === 'valid' ? 'Valid pipeline' :
                  pipeline.validationStatus === 'invalid' ? 'Validation failed' :
                  'Draft - not validated'}
                ${pipeline.validationErrors ? html`
                  <span>: ${pipeline.validationErrors}</span>
                ` : ''}
              </div>
            </div>
          `)}
        </div>
      `}

      ${this.showModal ? html`
        <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000;" @click=${(e) => e.target === e.currentTarget && this.closeModal()}>
          <div style="background: white; border-radius: 8px; width: 90%; height: 90%; max-width: 1400px; max-height: 900px; box-shadow: 0 4px 20px rgba(0,0,0,0.15); display: flex; flex-direction: column;">
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 20px; border-bottom: 1px solid #e0e0e0;">
              <h3 style="font-size: 20px; font-weight: 600; color: #333; margin: 0;">${this.editingPipeline ? 'Edit Pipeline' : 'Create Pipeline'}</h3>
              <button style="background: none; border: none; font-size: 24px; cursor: pointer; color: #999; padding: 0; width: 32px; height: 32px;" @click=${this.closeModal}>×</button>
            </div>
            <pipeline-builder 
              style="flex: 1; display: block; overflow: hidden;"
              .pipeline=${this.editingPipeline || { name: 'New Pipeline', nodes: [], connections: [] }}
              @save-pipeline=${(e) => this.savePipeline(e.detail.pipeline)}
              @pipeline-validated=${(e) => this.handleValidation(e.detail)}>
            </pipeline-builder>
          </div>
        </div>
      ` : ''}
    `;
  }
}

customElements.define('pipeline-view', PipelineView);