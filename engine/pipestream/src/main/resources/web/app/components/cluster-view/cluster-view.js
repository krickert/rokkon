import { LitElement, html, css } from 'lit';

export class ClusterView extends LitElement {
  static properties = {
    clusters: { type: Array },
    loading: { type: Boolean },
    error: { type: String },
    showModal: { type: Boolean },
    newClusterName: { type: String }
  };

  static styles = css`
    :host {
      display: block;
    }

    .cluster-header {
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

    .cluster-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 16px;
    }

    .cluster-card {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 20px;
      transition: all 0.2s ease;
    }

    .cluster-card:hover {
      border-color: #1976d2;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }

    .cluster-card.default {
      border-color: #4caf50;
      position: relative;
    }

    .default-badge {
      position: absolute;
      top: 10px;
      right: 10px;
      background: #4caf50;
      color: white;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 500;
    }

    .cluster-name {
      font-size: 18px;
      font-weight: 600;
      color: #333;
      margin-bottom: 8px;
    }

    .cluster-meta {
      display: grid;
      gap: 8px;
      font-size: 14px;
      color: #666;
      margin-bottom: 16px;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .meta-icon {
      width: 16px;
      height: 16px;
      opacity: 0.6;
    }

    .status-indicator {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 16px;
      font-size: 12px;
      font-weight: 500;
    }

    .status-indicator.active {
      background: #e8f5e9;
      color: #2e7d32;
    }

    .status-indicator.inactive {
      background: #f5f5f5;
      color: #999;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .status-dot.active {
      background: #4caf50;
    }

    .status-dot.inactive {
      background: #ccc;
    }

    .cluster-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 16px;
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

    .action-button.delete {
      color: #f44336;
      border-color: #f44336;
    }

    .action-button.delete:hover {
      background: #ffebee;
    }

    .action-button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
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

    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal {
      background: white;
      border-radius: 8px;
      padding: 24px;
      min-width: 400px;
      max-width: 500px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .modal-title {
      font-size: 20px;
      font-weight: 600;
      color: #333;
    }

    .modal-close {
      background: none;
      border: none;
      font-size: 24px;
      cursor: pointer;
      color: #999;
      padding: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .modal-close:hover {
      color: #333;
    }

    .modal-body {
      margin-bottom: 24px;
    }

    .form-group {
      margin-bottom: 16px;
    }

    .form-label {
      display: block;
      font-size: 14px;
      font-weight: 500;
      color: #333;
      margin-bottom: 8px;
    }

    .form-input {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
      box-sizing: border-box;
    }

    .form-input:focus {
      outline: none;
      border-color: #1976d2;
      box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.1);
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
    }

    .modal-button {
      padding: 8px 16px;
      border-radius: 4px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;
      border: 1px solid transparent;
    }

    .modal-button.cancel {
      background: white;
      border-color: #ddd;
      color: #666;
    }

    .modal-button.cancel:hover {
      background: #f5f5f5;
      border-color: #ccc;
    }

    .modal-button.primary {
      background: #1976d2;
      color: white;
    }

    .modal-button.primary:hover {
      background: #1565c0;
    }

    .modal-button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  `;

  constructor() {
    super();
    this.clusters = [];
    this.loading = true;
    this.error = null;
    this.showModal = false;
    this.newClusterName = '';
  }

  connectedCallback() {
    super.connectedCallback();
    this.fetchClusters();
  }

  async fetchClusters() {
    try {
      this.loading = true;
      const response = await fetch('/api/v1/clusters');
      if (!response.ok) throw new Error('Failed to fetch clusters');
      
      this.clusters = await response.json();
      this.loading = false;
    } catch (err) {
      this.error = err.message;
      this.loading = false;
    }
  }

  refresh() {
    this.fetchClusters();
  }

  createCluster() {
    this.showModal = true;
    this.newClusterName = '';
  }

  closeModal() {
    this.showModal = false;
    this.newClusterName = '';
  }

  async submitCluster() {
    if (!this.newClusterName.trim()) {
      this.showToast('Please enter a cluster name', 'error');
      return;
    }

    try {
      const response = await fetch(`/api/v1/clusters/${encodeURIComponent(this.newClusterName.trim())}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        }
      });
      
      if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'Failed to create cluster');
      }
      
      this.showToast('Cluster created successfully', 'success');
      this.closeModal();
      this.fetchClusters();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  async deleteCluster(cluster) {
    if (cluster.isDefault) {
      this.showToast('Cannot delete default cluster', 'error');
      return;
    }

    if (!confirm(`Are you sure you want to delete cluster "${cluster.name}"?`)) {
      return;
    }

    try {
      const response = await fetch(`/api/v1/clusters/${encodeURIComponent(cluster.name)}`, {
        method: 'DELETE'
      });
      if (!response.ok) throw new Error('Failed to delete cluster');
      
      this.showToast('Cluster deleted successfully', 'success');
      this.fetchClusters();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  showToast(message, type) {
    const event = new CustomEvent('show-toast', {
      detail: { message, type },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  formatDate(timestamp) {
    if (!timestamp) return 'Unknown';
    const date = new Date(timestamp);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  render() {
    if (this.loading) {
      return html`<div class="loading">Loading clusters...</div>`;
    }

    if (this.error) {
      return html`<div class="error">Error: ${this.error}</div>`;
    }

    return html`
      <div class="cluster-header">
        <h2 class="section-title">Clusters</h2>
        <button class="create-button" @click=${this.createCluster}>
          <svg class="plus-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
          </svg>
          Create Cluster
        </button>
      </div>

      ${this.clusters.length === 0 ? html`
        <div class="empty-state">
          <svg class="empty-icon" fill="currentColor" viewBox="0 0 20 20">
            <path d="M13 7H7v6h6V7z"/>
            <path fill-rule="evenodd" d="M7 2a1 1 0 012 0v1h2V2a1 1 0 112 0v1h2a2 2 0 012 2v2h1a1 1 0 110 2h-1v2h1a1 1 0 110 2h-1v2a2 2 0 01-2 2h-2v1a1 1 0 11-2 0v-1H9v1a1 1 0 11-2 0v-1H5a2 2 0 01-2-2v-2H2a1 1 0 110-2h1V9H2a1 1 0 010-2h1V5a2 2 0 012-2h2V2z" clip-rule="evenodd"/>
          </svg>
          <p>No clusters found</p>
          <p>Create your first cluster to organize pipelines</p>
        </div>
      ` : html`
        <div class="cluster-grid">
          ${this.clusters.map(cluster => html`
            <div class="cluster-card ${cluster.isDefault ? 'default' : ''}">
              ${cluster.isDefault ? html`
                <span class="default-badge">DEFAULT</span>
              ` : ''}
              
              <div class="cluster-name">${cluster.name}</div>
              
              <div class="cluster-meta">
                <div class="meta-item">
                  <svg class="meta-icon" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-6-3a2 2 0 11-4 0 2 2 0 014 0zm-2 4a5 5 0 00-4.546 2.916A5.986 5.986 0 0010 16a5.986 5.986 0 004.546-2.084A5 5 0 0010 11z" clip-rule="evenodd"/>
                  </svg>
                  <span>Version: ${cluster.version || '1.0.0'}</span>
                </div>
                <div class="meta-item">
                  <svg class="meta-icon" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zM4 8h12v8H4V8z" clip-rule="evenodd"/>
                  </svg>
                  <span>Created: ${this.formatDate(cluster.createdAt)}</span>
                </div>
                <div class="meta-item">
                  <span class="status-indicator ${cluster.status || 'active'}">
                    <span class="status-dot ${cluster.status || 'active'}"></span>
                    ${cluster.status === 'inactive' ? 'Inactive' : 'Active'}
                  </span>
                </div>
              </div>

              <div class="cluster-actions">
                <button 
                  class="action-button delete" 
                  @click=${() => this.deleteCluster(cluster)}
                  ?disabled=${cluster.isDefault}>
                  Delete
                </button>
              </div>
            </div>
          `)}
        </div>
      `}

      ${this.showModal ? html`
        <div class="modal-overlay" @click=${(e) => e.target === e.currentTarget && this.closeModal()}>
          <div class="modal">
            <div class="modal-header">
              <h3 class="modal-title">Create New Cluster</h3>
              <button class="modal-close" @click=${this.closeModal}>×</button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label class="form-label">Cluster Name</label>
                <input 
                  type="text" 
                  class="form-input"
                  placeholder="Enter cluster name"
                  .value=${this.newClusterName}
                  @input=${(e) => this.newClusterName = e.target.value}
                  @keyup=${(e) => e.key === 'Enter' && this.submitCluster()}>
              </div>
            </div>
            <div class="modal-footer">
              <button class="modal-button cancel" @click=${this.closeModal}>Cancel</button>
              <button 
                class="modal-button primary" 
                @click=${this.submitCluster}
                ?disabled=${!this.newClusterName.trim()}>
                Create Cluster
              </button>
            </div>
          </div>
        </div>
      ` : ''}
    `;
  }
}

customElements.define('cluster-view', ClusterView);