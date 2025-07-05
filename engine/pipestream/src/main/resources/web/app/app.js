import { LitElement, html, css } from 'lit';
import './components/service-box/service-box.js';
import './components/dashboard-grid/dashboard-grid.js';
import './components/navigation-header/navigation-header.js';
import './components/pipeline-view/pipeline-view.js';
import './components/cluster-view/cluster-view.js';
import './components/toast-notifications/toast-notifications.js';

class PipelineDashboard extends LitElement {
  static properties = {
    services: { type: Object },
    loading: { type: Boolean },
    error: { type: String },
    activeTab: { type: String },
    engineInfo: { type: Object },
    autoRefresh: { type: Boolean },
    refreshInterval: { type: Number },
    menuOpen: { type: Boolean },
    isDevMode: { type: Boolean },
    orphanedModules: { type: Array }
  };

  static styles = css`
    :host {
      display: block;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: #f5f5f5;
      min-height: 100vh;
    }

    .main-content {
      padding: 20px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .error {
      background: #fee;
      border: 1px solid #fcc;
      border-radius: 4px;
      padding: 16px;
      margin-bottom: 20px;
      color: #c00;
    }

    .loading {
      text-align: center;
      padding: 40px;
      color: #666;
    }

    .tab-navigation {
      display: flex;
      gap: 0;
      margin-bottom: 20px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      overflow: hidden;
    }

    .tab-button {
      flex: 1;
      padding: 12px 24px;
      background: none;
      border: none;
      cursor: pointer;
      font-size: 16px;
      font-weight: 500;
      color: #666;
      transition: all 0.2s ease;
      position: relative;
    }

    .tab-button:hover {
      background: #f5f5f5;
    }

    .tab-button.active {
      color: #1976d2;
      background: #e3f2fd;
    }

    .tab-button.active::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: #1976d2;
    }

    .hamburger-menu {
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 1000;
    }

    .menu-button {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 8px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      transition: all 0.2s ease;
    }

    .menu-button:hover {
      background: #f5f5f5;
      border-color: #1976d2;
    }

    .menu-icon {
      width: 24px;
      height: 24px;
    }

    .menu-dropdown {
      position: absolute;
      top: 50px;
      right: 0;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      min-width: 200px;
      display: none;
    }

    .menu-dropdown.open {
      display: block;
    }

    .menu-item {
      display: block;
      padding: 12px 16px;
      text-decoration: none;
      color: #333;
      transition: all 0.2s ease;
      border-bottom: 1px solid #e0e0e0;
    }

    .menu-item:last-child {
      border-bottom: none;
    }

    .menu-item:hover {
      background: #f5f5f5;
      color: #1976d2;
    }

    .menu-item-icon {
      width: 18px;
      height: 18px;
      margin-right: 8px;
      vertical-align: middle;
    }

    .dev-badge {
      background: #ff9800;
      color: white;
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 4px;
      margin-left: 8px;
      vertical-align: middle;
    }
  `;

  constructor() {
    super();
    this.services = { baseServices: [], moduleServices: [], statistics: {} };
    this.loading = true;
    this.error = null;
    this.activeTab = 'services';
    this.engineInfo = null;
    this.autoRefresh = localStorage.getItem('autoRefresh') === 'true';
    this.refreshInterval = parseInt(localStorage.getItem('refreshInterval') || '30000');
    this.menuOpen = false;
    this.isDevMode = false;
    this.availableModules = [];
    this.deployedModules = [];
    this.deployingModules = [];
  }

  connectedCallback() {
    super.connectedCallback();
    console.log('PipelineDashboard connected, fetching data...');
    this.fetchEngineInfo();
    this.fetchDashboardData();
    this.setupAutoRefresh();
    
    // Listen for clicks outside menu
    this.boundCloseMenu = this.closeMenu.bind(this);
    document.addEventListener('click', this.boundCloseMenu);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this.interval) {
      clearInterval(this.interval);
    }
    document.removeEventListener('click', this.boundCloseMenu);
  }

  handleRegisterModule() {
    // For now, show a prompt for module registration
    const moduleName = prompt('Enter module name to register:');
    if (moduleName) {
      this.showToast(`Module registration for '${moduleName}' coming soon`, 'info');
    }
  }

  async handleCleanupZombies() {
    try {
      const response = await fetch('/api/v1/modules/cleanup-zombies', {
        method: 'POST'
      });
      if (!response.ok) throw new Error('Failed to cleanup zombies');
      
      const result = await response.json();
      this.showToast(`Cleaned up ${result.removed || 0} zombie services`, 'success');
      this.fetchDashboardData();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  async handleDeployModule(event) {
    const { module } = event.detail;
    
    try {
      // WebSocket will handle the deploying state updates
      this.showToast(`Deploying ${module.name} module...`, 'info');
      
      const response = await fetch(`/api/v1/module-management/${module.name}/deploy`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      });
      
      // Check for both 200 OK and 202 Accepted
      if (!response.ok && response.status !== 202) {
        let errorMessage = `Failed to deploy ${module.name}`;
        try {
          const error = await response.json();
          errorMessage = error.message || errorMessage;
        } catch (e) {
          // If JSON parsing fails, use status text
          errorMessage = response.statusText || errorMessage;
        }
        throw new Error(errorMessage);
      }
      
      const result = await response.json();
      // WebSocket will notify about deployment success
      
      // Immediate refresh to show deployment status
      this.fetchDeployedModules();
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  extractModuleName(instance, service) {
    // Try to extract module name from various sources
    if (instance.module_id && instance.module_id.includes('-module-')) {
      // Extract from module_id like "echo-module-d6d9605b"
      return instance.module_id.split('-module-')[0];
    } else if (service && service.name && service.name.includes('-module')) {
      // Extract from service name like "echo-module"
      return service.name.split('-module')[0];
    } else if (instance.metadata && instance.metadata.moduleName) {
      // Extract from metadata if available
      return instance.metadata.moduleName.replace('-module', '');
    }
    // Default fallback
    return instance.module_id || instance.id || 'unknown';
  }

  async handleModuleAction(event) {
    const { action, instance, service } = event.detail;
    try {
      let endpoint = '';
      let method = 'POST';
      
      switch (action) {
        case 'register':
          endpoint = `/api/v1/modules/${instance.id}/register`;
          break;
        case 'deregister':
          endpoint = `/api/v1/modules/${instance.module_id || instance.id}`;
          method = 'DELETE';
          break;
        case 'enable':
          endpoint = `/api/v1/modules/${instance.module_id || instance.id}/enable`;
          method = 'PUT';
          break;
        case 'disable':
          endpoint = `/api/v1/modules/${instance.module_id || instance.id}/disable`;
          method = 'PUT';
          break;
        case 'undeploy':
          // For dev mode - undeploy the Docker container
          // Extract module name from service name or module_id
          let moduleNameForUndeploy = this.extractModuleName(instance, service);
          
          // Use the module_id to identify which specific instance to remove
          if (instance.module_id) {
            endpoint = `/api/v1/module-management/${moduleNameForUndeploy}/instance/${instance.module_id}`;
          } else {
            // Fallback to removing all instances
            endpoint = `/api/v1/module-management/${moduleNameForUndeploy}/undeploy`;
          }
          method = 'DELETE';
          break;
        case 'scale-up':
          // For dev mode - deploy additional instance
          let moduleNameForScale = this.extractModuleName(instance, service);
          endpoint = `/api/v1/module-management/${moduleNameForScale}/scale-up`;
          method = 'POST';
          break;
      }
      
      const response = await fetch(endpoint, { method });
      if (!response.ok) throw new Error(`Failed to ${action} module`);
      
      this.showToast(`Module ${action} successful`, 'success');
      this.fetchDashboardData();
      
      // For dev mode actions that affect deployment status, refresh module lists
      if (this.isDevMode && (action === 'undeploy' || action === 'deploy')) {
        // Add a small delay for undeploy to complete
        setTimeout(() => {
          this.fetchDeployedModules();
          this.fetchAvailableModules();
        }, 500);
      }
    } catch (err) {
      this.showToast(err.message, 'error');
    }
  }

  showToast(message, type = 'info') {
    const event = new CustomEvent('show-toast', {
      detail: { message, type },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  setupAutoRefresh() {
    if (this.interval) {
      clearInterval(this.interval);
    }
    
    if (this.autoRefresh) {
      this.interval = setInterval(() => {
        this.fetchDashboardData();
        if (this.activeTab === 'pipelines') {
          this.shadowRoot.querySelector('pipeline-view')?.refresh();
        } else if (this.activeTab === 'clusters') {
          this.shadowRoot.querySelector('cluster-view')?.refresh();
        }
      }, this.refreshInterval);
    }
  }

  toggleAutoRefresh() {
    this.autoRefresh = !this.autoRefresh;
    localStorage.setItem('autoRefresh', this.autoRefresh.toString());
    this.setupAutoRefresh();
  }

  async fetchEngineInfo() {
    try {
      const response = await fetch('/api/v1/engine/info');
      if (response.ok) {
        this.engineInfo = await response.json();
        this.isDevMode = this.engineInfo?.profile === 'dev';
        
        // Fetch available modules in dev mode
        if (this.isDevMode) {
          this.fetchAvailableModules();
          this.fetchDeployedModules();
          this.fetchOrphanedModules();
        }
      }
    } catch (err) {
      console.error('Failed to fetch engine info:', err);
    }
  }

  async fetchAvailableModules() {
    try {
      const response = await fetch('/api/v1/module-management/available');
      if (response.ok) {
        this.availableModules = await response.json();
        console.log('Available modules:', this.availableModules);
      }
    } catch (err) {
      console.error('Failed to fetch available modules:', err);
    }
  }

  async fetchDeployedModules() {
    try {
      const response = await fetch('/api/v1/module-management/deployed');
      if (response.ok) {
        this.deployedModules = await response.json();
        console.log('Deployed modules updated:', this.deployedModules);
        // Force update of child components
        this.requestUpdate();
      }
    } catch (err) {
      console.error('Failed to fetch deployed modules:', err);
    }
  }

  async fetchOrphanedModules() {
    try {
      const response = await fetch('/api/v1/module-management/orphaned');
      if (response.ok) {
        this.orphanedModules = await response.json();
        console.log('Orphaned modules:', this.orphanedModules);
        this.requestUpdate();
      }
    } catch (err) {
      console.error('Failed to fetch orphaned modules:', err);
    }
  }

  async fetchDashboardData() {
    try {
      console.log('Fetching dashboard data...');
      const response = await fetch('/api/v1/module-discovery/dashboard');
      if (!response.ok) throw new Error('Failed to fetch dashboard data');
      
      const data = await response.json();
      console.log('Dashboard data received:', data);
      this.services = data;
      this.loading = false;
      this.error = null;
      
      // Also refresh module deployment status in dev mode
      if (this.isDevMode) {
        this.fetchDeployedModules();
        this.fetchOrphanedModules();
      }
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
      this.error = err.message;
      this.loading = false;
    }
  }

  switchTab(tab) {
    this.activeTab = tab;
  }

  toggleMenu(e) {
    e.stopPropagation();
    this.menuOpen = !this.menuOpen;
  }

  closeMenu() {
    this.menuOpen = false;
  }

  renderMenuIcon() {
    return html`<svg class="menu-icon" fill="currentColor" viewBox="0 0 20 20">
      <path fill-rule="evenodd" d="M3 5a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 10a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 15a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clip-rule="evenodd"/>
    </svg>`;
  }

  render() {
    if (this.loading && !this.engineInfo) {
      return html`<div class="loading">Loading dashboard...</div>`;
    }

    return html`
      <navigation-header 
        .engineInfo=${this.engineInfo}
        .autoRefresh=${this.autoRefresh}
        .refreshInterval=${this.refreshInterval}
        @toggle-refresh=${() => this.toggleAutoRefresh()}
        @change-interval=${(e) => {
          this.refreshInterval = e.detail.interval;
          localStorage.setItem('refreshInterval', this.refreshInterval.toString());
          this.setupAutoRefresh();
        }}>
      </navigation-header>

      <div class="main-content">
        ${this.error ? html`<div class="error">Error: ${this.error}</div>` : ''}
        
        <div class="tab-navigation">
          <button 
            class="tab-button ${this.activeTab === 'services' ? 'active' : ''}"
            @click=${() => this.switchTab('services')}>
            Services
          </button>
          <button 
            class="tab-button ${this.activeTab === 'pipelines' ? 'active' : ''}"
            @click=${() => this.switchTab('pipelines')}>
            Pipelines
          </button>
          <button 
            class="tab-button ${this.activeTab === 'clusters' ? 'active' : ''}"
            @click=${() => this.switchTab('clusters')}>
            Clusters
          </button>
        </div>

        ${this.activeTab === 'services' ? html`
          <dashboard-grid 
            .baseServices=${this.services.base_services}
            .moduleServices=${this.services.module_services}
            .statistics=${this.services.statistics}
            .isDevMode=${this.isDevMode}
            .availableModules=${this.availableModules}
            .deployedModules=${this.deployedModules}
            .deployingModules=${this.deployingModules}
            .orphanedModules=${this.orphanedModules}
            @register-module=${this.handleRegisterModule}
            @cleanup-zombies=${this.handleCleanupZombies}
            @module-action=${this.handleModuleAction}
            @deploy-module=${this.handleDeployModule}
            @refresh-data=${() => {
              this.showToast('Refreshing...', 'info');
              this.fetchDashboardData();
              if (this.isDevMode) {
                this.fetchDeployedModules();
                this.fetchAvailableModules();
                this.fetchOrphanedModules();
              }
            }}>
          </dashboard-grid>
        ` : ''}

        ${this.activeTab === 'pipelines' ? html`
          <pipeline-view></pipeline-view>
        ` : ''}

        ${this.activeTab === 'clusters' ? html`
          <cluster-view></cluster-view>
        ` : ''}
      </div>

      <div class="hamburger-menu">
        <button class="menu-button" @click=${(e) => this.toggleMenu(e)}>
          ${this.renderMenuIcon()}
        </button>
        <div class="menu-dropdown ${this.menuOpen ? 'open' : ''}">
          <a href="/swagger-ui" class="menu-item" target="_blank">
            <svg class="menu-item-icon" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M12.316 3.051a1 1 0 01.633 1.265l-4 12a1 1 0 11-1.898-.632l4-12a1 1 0 011.265-.633zM5.707 6.293a1 1 0 010 1.414L3.414 10l2.293 2.293a1 1 0 11-1.414 1.414l-3-3a1 1 0 010-1.414l3-3a1 1 0 011.414 0zm8.586 0a1 1 0 011.414 0l3 3a1 1 0 010 1.414l-3 3a1 1 0 11-1.414-1.414L16.586 10l-2.293-2.293a1 1 0 010-1.414z" clip-rule="evenodd"/>
            </svg>
            Swagger UI
          </a>
          ${this.isDevMode ? html`
            <a href="/q/dev" class="menu-item" target="_blank">
              <svg class="menu-item-icon" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z" clip-rule="evenodd"/>
              </svg>
              Dev UI
              <span class="dev-badge">DEV</span>
            </a>
          ` : ''}
        </div>
      </div>

      <toast-notifications></toast-notifications>
    `;
  }
}

customElements.define('pipeline-dashboard', PipelineDashboard);

// Initialize the dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  const container = document.getElementById('app');
  if (container) {
    container.innerHTML = '<pipeline-dashboard></pipeline-dashboard>';
  }
});

// Export for web bundler
export { PipelineDashboard };