import { LitElement, html, css } from 'lit';
import '../service-box/service-box.js';
import '../module-deploy-dropdown/module-deploy-dropdown.js';
import { deploymentSSE } from '../../services/deployment-sse.js';

export class DashboardGrid extends LitElement {
  static properties = {
    baseServices: { type: Array },
    moduleServices: { type: Array },
    statistics: { type: Object },
    isDevMode: { type: Boolean },
    availableModules: { type: Array },
    deployedModules: { type: Array },
    deployingModules: { type: Array }
  };

  static styles = css`
    :host {
      display: block;
    }

    .stats-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 30px;
    }

    .stat-card {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 20px;
      text-align: center;
    }

    .stat-value {
      font-size: 32px;
      font-weight: 600;
      color: #1976d2;
      margin-bottom: 4px;
    }

    .stat-label {
      font-size: 14px;
      color: #666;
    }

    .service-section {
      margin-bottom: 40px;
    }

    .section-header {
      display: flex;
      align-items: center;
      margin-bottom: 20px;
      padding-bottom: 12px;
      border-bottom: 2px solid #e0e0e0;
    }

    .section-title {
      font-size: 20px;
      font-weight: 600;
      color: #333;
    }

    .section-count {
      margin-left: 12px;
      background: #e3f2fd;
      color: #1976d2;
      padding: 4px 12px;
      border-radius: 16px;
      font-size: 14px;
      font-weight: 500;
    }

    .service-group {
      margin-bottom: 24px;
    }

    .group-header {
      display: flex;
      align-items: center;
      margin-bottom: 12px;
    }

    .group-name {
      font-size: 16px;
      font-weight: 500;
      color: #555;
    }

    .duplicate-info {
      margin-left: 8px;
      background: #fff3cd;
      color: #856404;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 12px;
    }

    .instances-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 16px;
    }

    .empty-state {
      text-align: center;
      padding: 40px;
      color: #999;
    }

    .module-service {
      background: #f8f9fa;
      border: 1px solid #dee2e6;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 12px;
    }

    .module-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .module-name {
      font-weight: 600;
      font-size: 16px;
      color: #333;
    }

    .module-registered {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #28a745;
    }

    .check-icon {
      width: 16px;
      height: 16px;
    }

    .actions-section {
      display: flex;
      gap: 12px;
      margin-bottom: 20px;
      flex-wrap: wrap;
      align-items: center;
    }

    .action-button {
      background: white;
      border: 1px solid #e0e0e0;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }

    .action-button:hover {
      background: #f5f5f5;
      border-color: #1976d2;
    }

    .action-button.primary {
      background: #1976d2;
      color: white;
      border-color: #1976d2;
    }

    .action-button.primary:hover {
      background: #1565c0;
    }

    .action-button.danger {
      background: #f44336;
      color: white;
      border-color: #f44336;
    }

    .action-button.danger:hover {
      background: #e53935;
    }

    .zombie-alert {
      background: #fff3cd;
      border: 1px solid #ffeaa7;
      border-radius: 6px;
      padding: 12px 16px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      gap: 12px;
      color: #856404;
    }

    .alert-icon {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    .button-icon {
      width: 18px;
      height: 18px;
    }

    .deploying-card {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 12px;
      padding: 24px;
      margin-bottom: 16px;
      color: white;
      box-shadow: 0 8px 24px rgba(102, 126, 234, 0.2);
      animation: pulse-glow 2s ease-in-out infinite;
    }

    @keyframes pulse-glow {
      0%, 100% {
        box-shadow: 0 8px 24px rgba(102, 126, 234, 0.2);
      }
      50% {
        box-shadow: 0 12px 32px rgba(102, 126, 234, 0.4);
      }
    }

    .deploying-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 12px;
    }

    .rocket-icon {
      width: 48px;
      height: 48px;
      animation: rocket-bounce 1s ease-in-out infinite;
    }

    @keyframes rocket-bounce {
      0%, 100% {
        transform: translateY(0);
      }
      50% {
        transform: translateY(-8px);
      }
    }

    .deploying-title {
      font-size: 24px;
      font-weight: 600;
    }

    .deploying-subtitle {
      font-size: 14px;
      opacity: 0.9;
      margin-top: 4px;
    }

    .deploying-progress {
      margin-top: 16px;
      height: 4px;
      background: rgba(255, 255, 255, 0.3);
      border-radius: 2px;
      overflow: hidden;
    }

    .deploying-progress-bar {
      height: 100%;
      background: white;
      border-radius: 2px;
      animation: progress-slide 2s ease-in-out infinite;
    }

    @keyframes progress-slide {
      0% {
        width: 0;
        transform: translateX(0);
      }
      50% {
        width: 100%;
        transform: translateX(0);
      }
      100% {
        width: 100%;
        transform: translateX(100%);
      }
    }

    .deployment-status {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.8);
      margin-top: 8px;
      font-style: italic;
    }
  `;

  constructor() {
    super();
    this.deploymentStatuses = new Map(); // Track deployment status messages
  }

  connectedCallback() {
    super.connectedCallback();
    
    // Connect to SSE if in dev mode
    if (this.isDevMode) {
      this.connectSSE();
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    
    // Remove SSE listener
    if (this.isDevMode) {
      deploymentSSE.removeListener('dashboard-grid');
    }
  }

  async connectSSE() {
    try {
      await deploymentSSE.connect();
      
      // Add listener for deployment events
      deploymentSSE.addListener('dashboard-grid', (event) => {
        this.handleDeploymentEvent(event);
      });
    } catch (error) {
      console.error('Failed to connect to deployment SSE:', error);
    }
  }

  handleDeploymentEvent(event) {
    console.log('Deployment event:', event);
    
    switch (event.type) {
      case 'deployment_started':
        this.deployingModules = [...(this.deployingModules || []), event.module];
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        break;
        
      case 'deployment_progress':
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        break;
        
      case 'deployment_success':
        // Keep showing for a bit longer
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        
        // Remove after 3 seconds
        setTimeout(() => {
          this.deployingModules = this.deployingModules.filter(m => m !== event.module);
          this.deploymentStatuses.delete(event.module);
          this.requestUpdate();
          
          // Fire refresh event to update module list
          this.dispatchEvent(new CustomEvent('refresh-data', {
            bubbles: true,
            composed: true
          }));
        }, 3000);
        break;
        
      case 'deployment_failed':
        this.deployingModules = this.deployingModules.filter(m => m !== event.module);
        this.deploymentStatuses.delete(event.module);
        this.requestUpdate();
        
        // Show error toast
        this.dispatchEvent(new CustomEvent('show-toast', {
          detail: { message: event.message, type: 'error' },
          bubbles: true,
          composed: true
        }));
        break;
        
      case 'module_registered':
        // Show success toast
        this.dispatchEvent(new CustomEvent('show-toast', {
          detail: { message: `${event.module} registered successfully!`, type: 'success' },
          bubbles: true,
          composed: true
        }));
        
        // Refresh data
        this.dispatchEvent(new CustomEvent('refresh-data', {
          bubbles: true,
          composed: true
        }));
        break;
    }
  }

  renderGroupedInstances(services) {
    // Group instances by service name only (for Consul services)
    const grouped = new Map();
    
    services.forEach(service => {
      const key = service.name;
      
      if (!grouped.has(key)) {
        grouped.set(key, {
          service,
          instances: service.instances,
          primaryInstance: service.instances[0]
        });
      }
    });
    
    // Render grouped instances
    return Array.from(grouped.values()).map(group => {
      const count = group.instances.length;
      
      if (count === 1) {
        // Single instance, render normally
        return html`
          <service-box 
            .service=${group.service} 
            .instance=${group.primaryInstance}
            .isDevMode=${this.isDevMode}>
          </service-box>
        `;
      } else {
        // Multiple instances, render with stack effect
        return html`
          <service-box 
            .service=${group.service} 
            .instance=${group.primaryInstance}
            .instanceCount=${count}
            .allInstances=${group.instances}
            .isDevMode=${this.isDevMode}>
          </service-box>
        `;
      }
    });
  }

  registerModule() {
    const event = new CustomEvent('register-module', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  cleanupZombies() {
    const event = new CustomEvent('cleanup-zombies', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  refreshData() {
    const event = new CustomEvent('refresh-data', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  handleDeployModule(event) {
    // Forward the event to parent
    this.dispatchEvent(new CustomEvent('deploy-module', {
      detail: event.detail,
      bubbles: true,
      composed: true
    }));
  }

  render() {
    const { baseServices = [], moduleServices = [], statistics = {} } = this;
    const hasZombies = (statistics.zombie_count || 0) > 0;

    return html`
      ${hasZombies ? html`
        <div class="zombie-alert">
          <svg class="alert-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
          </svg>
          <span>${statistics.zombie_count} zombie service${statistics.zombie_count > 1 ? 's' : ''} detected</span>
        </div>
      ` : ''}

      <div class="actions-section">
        <button class="action-button" @click=${this.refreshData}>
          <svg class="button-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd"/>
          </svg>
          Refresh
        </button>
        <button class="action-button primary" @click=${this.registerModule}>
          <svg class="button-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
          </svg>
          Register Module
        </button>
        ${hasZombies ? html`
          <button class="action-button danger" @click=${this.cleanupZombies}>
            <svg class="button-icon" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm4 0a1 1 0 012 0v6a1 1 0 11-2 0V8z" clip-rule="evenodd"/>
            </svg>
            Cleanup Zombies
          </button>
        ` : ''}
      </div>

      <div class="stats-bar">
        <div class="stat-card">
          <div class="stat-value">${statistics.total_base_services || 0}</div>
          <div class="stat-label">Base Services</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">${statistics.total_modules || 0}</div>
          <div class="stat-label">Total Modules</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">${statistics.healthy_modules || 0}</div>
          <div class="stat-label">Healthy Modules</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">${statistics.zombie_count || 0}</div>
          <div class="stat-label">Zombie Services</div>
        </div>
      </div>

      <div class="service-section">
        <div class="section-header">
          <h2 class="section-title">Base Services</h2>
          <span class="section-count">${baseServices.length}</span>
        </div>

        ${baseServices.length === 0 ? html`
          <div class="empty-state">No base services found</div>
        ` : html`
          <div class="instances-grid">
            ${this.renderGroupedInstances(baseServices)}
          </div>
        `}
      </div>

      <div class="service-section">
        <div class="section-header">
          <div style="display: flex; align-items: center; gap: 12px;">
            <h2 class="section-title">Module Services</h2>
            <span class="section-count">${moduleServices.length}</span>
          </div>
          ${this.isDevMode ? html`
            <module-deploy-dropdown
              .availableModules=${this.availableModules || []}
              .deployedModules=${this.deployedModules || []}
              @deploy-module=${this.handleDeployModule}>
            </module-deploy-dropdown>
          ` : ''}
        </div>

        ${this.deployingModules && this.deployingModules.length > 0 ? html`
          ${this.deployingModules.map(moduleName => html`
            <div class="deploying-card">
              <div class="deploying-header">
                <svg class="rocket-icon" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M9.19 6.35c-2.04 2.29-3.44 5.58-3.57 5.89L2 10.69l4.05-4.05c.47-.47 1.15-.68 1.81-.55zM11.17 17c0 .7-.28 1.38-.78 1.88l-2.33 2.33-1.55-3.62c.31-.13 3.6-1.53 5.89-3.57.13.66-.08 1.33-.55 1.8zM17 7c2.76 0 5-2.24 5-5 0-1.11-.9-2-2-2-2.76 0-5 2.24-5 5 0 .38.04.74.12 1.08L9.91 11.3c-.32.32-.59.66-.82 1.02l-.86-.35c-.47-.19-1.01-.14-1.44.13L2 17l4.89 4.89 4.89-4.89c.27-.42.32-.97.13-1.44l-.35-.86c.36-.23.7-.5 1.02-.82l5.21-5.21C17.26 6.96 17.62 7 18 7c1.11 0 2-.9 2-2 0-.38-.04-.74-.12-1.08.74.08 1.46.12 1.84.12z"/>
                </svg>
                <div>
                  <div class="deploying-title">Deploying ${moduleName}...</div>
                  <div class="deploying-subtitle">Starting module with Consul sidecar</div>
                </div>
              </div>
              <div class="deploying-progress">
                <div class="deploying-progress-bar"></div>
              </div>
              ${this.deploymentStatuses.has(moduleName) ? html`
                <div class="deployment-status">${this.deploymentStatuses.get(moduleName)}</div>
              ` : ''}
            </div>
          `)}
        ` : ''}

        ${moduleServices.length === 0 && (!this.deployingModules || this.deployingModules.length === 0) ? html`
          <div class="empty-state">No module services found</div>
        ` : moduleServices.map(module => html`
          <div class="module-service">
            <div class="module-header">
              <div class="module-name">${module.module_name}</div>
              ${module.registered ? html`
                <div class="module-registered">
                  <svg class="check-icon" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"/>
                  </svg>
                  Registered
                </div>
              ` : ''}
            </div>
            <div class="instances-grid">
              ${module.instances.map(instance => html`
                <service-box 
                  .service=${{ name: module.service_name, type: 'module' }} 
                  .instance=${instance}
                  .isDevMode=${this.isDevMode}>
                </service-box>
              `)}
            </div>
          </div>
        `)}
      </div>
    `;
  }
}

customElements.define('dashboard-grid', DashboardGrid);