import { LitElement, html, css } from 'lit';

export class ModuleDeployDropdown extends LitElement {
  static properties = {
    availableModules: { type: Array },
    deployedModules: { type: Array },
    isOpen: { type: Boolean },
    searchTerm: { type: String },
    loading: { type: Boolean },
    selectedModule: { type: String }
  };

  static styles = css`
    :host {
      display: inline-block;
      position: relative;
    }

    .deploy-button {
      background: #1976d2;
      color: white;
      border: none;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }

    .deploy-button:hover {
      background: #1565c0;
    }

    .deploy-button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }

    .dropdown-icon {
      width: 16px;
      height: 16px;
      transition: transform 0.2s ease;
    }

    .dropdown-icon.open {
      transform: rotate(180deg);
    }

    .dropdown-container {
      position: absolute;
      top: 100%;
      left: 0;
      margin-top: 8px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      min-width: 280px;
      max-height: 400px;
      z-index: 1000;
      display: none;
      flex-direction: column;
    }

    .dropdown-container.open {
      display: flex;
    }

    .search-container {
      padding: 12px;
      border-bottom: 1px solid #e0e0e0;
    }

    .search-input {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      font-size: 14px;
      outline: none;
      transition: border-color 0.2s ease;
    }

    .search-input:focus {
      border-color: #1976d2;
    }

    .modules-list {
      flex: 1;
      overflow-y: auto;
      max-height: 300px;
    }

    .module-item {
      padding: 12px 16px;
      cursor: pointer;
      transition: background 0.2s ease;
      border-bottom: 1px solid #f0f0f0;
    }

    .module-item:last-child {
      border-bottom: none;
    }

    .module-item:hover {
      background: #f5f5f5;
    }

    .module-item.disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .module-item.disabled:hover {
      background: transparent;
    }

    .module-name {
      font-weight: 500;
      font-size: 15px;
      color: #333;
      margin-bottom: 4px;
    }

    .module-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 13px;
      color: #666;
    }

    .module-type {
      background: #e3f2fd;
      color: #1976d2;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 11px;
      text-transform: uppercase;
    }

    .module-status {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #28a745;
    }

    .status-icon {
      width: 12px;
      height: 12px;
    }

    .empty-state {
      padding: 24px;
      text-align: center;
      color: #999;
      font-size: 14px;
    }

    .loading-state {
      padding: 24px;
      text-align: center;
      color: #666;
    }

    .plus-icon {
      width: 18px;
      height: 18px;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .loading-spinner {
      width: 20px;
      height: 20px;
      border: 2px solid #f3f3f3;
      border-top: 2px solid #1976d2;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto;
    }
  `;

  constructor() {
    super();
    this.availableModules = [];
    this.deployedModules = [];
    this.isOpen = false;
    this.searchTerm = '';
    this.loading = false;
    this.selectedModule = null;
  }

  connectedCallback() {
    super.connectedCallback();
    // Close dropdown when clicking outside
    this.boundCloseDropdown = this.closeDropdown.bind(this);
    document.addEventListener('click', this.boundCloseDropdown);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('click', this.boundCloseDropdown);
  }

  closeDropdown(e) {
    if (!this.contains(e.target)) {
      this.isOpen = false;
    }
  }

  toggleDropdown(e) {
    e.stopPropagation();
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      // Focus search input when opening
      setTimeout(() => {
        const input = this.shadowRoot.querySelector('.search-input');
        if (input) input.focus();
      }, 0);
    }
  }

  handleSearch(e) {
    this.searchTerm = e.target.value.toLowerCase();
  }

  get filteredModules() {
    if (!this.searchTerm) return this.availableModules;
    
    return this.availableModules.filter(module => 
      module.name.toLowerCase().includes(this.searchTerm) ||
      module.type.toLowerCase().includes(this.searchTerm)
    );
  }

  isModuleDeployed(moduleName) {
    return this.deployedModules.some(deployed => 
      deployed.name.toLowerCase() === moduleName.toLowerCase()
    );
  }

  async deployModule(module) {
    if (this.loading || this.isModuleDeployed(module.name)) return;

    this.loading = true;
    this.selectedModule = module.name;

    const event = new CustomEvent('deploy-module', {
      detail: { module },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);

    // Close dropdown after selection
    this.isOpen = false;
    this.searchTerm = '';
    
    // Reset loading state after a delay (parent will handle actual loading)
    setTimeout(() => {
      this.loading = false;
      this.selectedModule = null;
    }, 1000);
  }

  render() {
    return html`
      <button 
        class="deploy-button" 
        @click=${this.toggleDropdown}
        ?disabled=${this.loading}>
        ${this.loading ? html`
          <div class="loading-spinner"></div>
          <span>Deploying...</span>
        ` : html`
          <svg class="plus-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
          </svg>
          <span>Deploy Module</span>
          <svg class="dropdown-icon ${this.isOpen ? 'open' : ''}" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/>
          </svg>
        `}
      </button>

      <div class="dropdown-container ${this.isOpen ? 'open' : ''}">
        <div class="search-container">
          <input 
            type="text" 
            class="search-input" 
            placeholder="Search modules..."
            .value=${this.searchTerm}
            @input=${this.handleSearch}
            @click=${(e) => e.stopPropagation()}>
        </div>

        <div class="modules-list">
          ${this.filteredModules.length === 0 ? html`
            <div class="empty-state">
              ${this.searchTerm ? 'No modules match your search' : 'No modules available'}
            </div>
          ` : this.filteredModules.map(module => {
            const isDeployed = this.isModuleDeployed(module.name);
            return html`
              <div 
                class="module-item ${isDeployed ? 'disabled' : ''}"
                @click=${() => this.deployModule(module)}>
                <div class="module-name">${module.name}</div>
                <div class="module-info">
                  <div>
                    <span class="module-type">${module.type}</span>
                    <span style="margin-left: 8px; color: #999;">Port: ${module.default_ports?.unified || 'N/A'}</span>
                  </div>
                  ${isDeployed ? html`
                    <div class="module-status">
                      <svg class="status-icon" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
                      </svg>
                      Deployed
                    </div>
                  ` : ''}
                </div>
              </div>
            `;
          })}
        </div>
      </div>
    `;
  }
}

customElements.define('module-deploy-dropdown', ModuleDeployDropdown);