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
    deployingModules: { type: Array },
    undeployingModules: { type: Array },
    healthCheckingModules: { type: Array },
    cleaningZombies: { type: Boolean },
    orphanedModules: { type: Array },
    registeringModules: { type: Array }
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

    .section-icon {
      width: 24px;
      height: 24px;
      margin-right: 8px;
      fill: #1976d2;
    }

    .observability-icon {
      width: 20px;
      height: 20px;
      fill: currentColor;
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

    .orphaned-section {
      background: #fff3e0;
      border: 1px solid #ffb74d;
      border-radius: 8px;
      padding: 20px;
      margin-bottom: 30px;
    }

    .orphaned-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }

    .orphaned-icon {
      width: 24px;
      height: 24px;
      fill: #f57c00;
    }

    .orphaned-title {
      font-size: 18px;
      font-weight: 600;
      color: #e65100;
    }

    .orphaned-description {
      color: #bf360c;
      font-size: 14px;
      margin-bottom: 16px;
    }

    .orphaned-modules-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 16px;
    }

    .orphaned-module-card {
      background: white;
      border: 1px solid #ffb74d;
      border-radius: 6px;
      padding: 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .orphaned-module-info {
      flex: 1;
    }

    .orphaned-module-name {
      font-weight: 600;
      color: #333;
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    
    .container-type-badge {
      background: #f0f0f0;
      color: #666;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 500;
      text-transform: uppercase;
    }
    
    .container-type-badge.module {
      background: #e3f2fd;
      color: #1976d2;
    }
    
    .container-type-badge.sidecar {
      background: #f3e5f5;
      color: #7b1fa2;
    }
    
    .container-type-badge.registrar {
      background: #fff3e0;
      color: #f57c00;
    }

    .orphaned-module-details {
      font-size: 12px;
      color: #666;
    }

    .register-button {
      background: #ff6f00;
      color: white;
      border: none;
      border-radius: 4px;
      padding: 8px 16px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: background 0.2s;
    }

    .register-button:hover {
      background: #e65100;
    }

    .register-button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }

    .register-button.registering {
      background: #4caf50;
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

    .undeploying-card {
      background: linear-gradient(135deg, #616161 0%, #424242 100%);
      border-radius: 12px;
      padding: 24px;
      margin-bottom: 16px;
      color: white;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
      animation: fade-out 3s ease-in-out forwards;
      opacity: 0.9;
    }

    @keyframes fade-out {
      0% {
        opacity: 0.9;
        transform: scale(1);
      }
      50% {
        opacity: 0.6;
        transform: scale(0.98);
      }
      100% {
        opacity: 0.3;
        transform: scale(0.95);
      }
    }

    .undeploying-header {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .gravestone-icon {
      width: 48px;
      height: 48px;
    }

    .undeploying-title {
      font-size: 24px;
      font-weight: 600;
      opacity: 0.8;
    }

    .undeploying-subtitle {
      font-size: 14px;
      opacity: 0.6;
      margin-top: 4px;
    }

    .health-checking-card {
      background: linear-gradient(135deg, #4CAF50 0%, #81C784 100%);
      border-radius: 12px;
      padding: 24px;
      margin-bottom: 16px;
      color: white;
      box-shadow: 0 8px 24px rgba(76, 175, 80, 0.2);
      animation: health-pulse 2s ease-in-out infinite;
    }

    @keyframes health-pulse {
      0%, 100% {
        box-shadow: 0 8px 24px rgba(76, 175, 80, 0.2);
      }
      50% {
        box-shadow: 0 12px 32px rgba(76, 175, 80, 0.4);
      }
    }

    .health-checking-header {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .health-cross-icon {
      width: 48px;
      height: 48px;
    }

    .health-checking-title {
      font-size: 24px;
      font-weight: 600;
    }

    .health-checking-subtitle {
      font-size: 14px;
      opacity: 0.9;
      margin-top: 4px;
    }
  `;

  constructor() {
    super();
    this.deploymentStatuses = new Map(); // Track deployment status messages
    this.undeployingModules = [];
    this.healthCheckingModules = [];
    this.cleaningZombies = false;
    this.orphanedModules = [];
    this.registeringModules = [];
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
        // Avoid duplicates
        if (!this.deployingModules?.includes(event.module)) {
          this.deployingModules = [...(this.deployingModules || []), event.module];
        }
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        break;
        
      case 'deployment_progress':
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        break;
        
      case 'deployment_success':
        // Transition to health checking state
        this.deployingModules = this.deployingModules.filter(m => m !== event.module);
        this.healthCheckingModules = [...(this.healthCheckingModules || []), event.module];
        this.deploymentStatuses.set(event.module, 'Waiting for module to be healthy...');
        this.requestUpdate();
        
        // Registration starts after 15 seconds, check at 16 seconds
        setTimeout(() => {
          this.dispatchEvent(new CustomEvent('refresh-data', {
            bubbles: true,
            composed: true
          }));
        }, 16000);
        
        // Registration usually completes by 20 seconds
        setTimeout(() => {
          this.dispatchEvent(new CustomEvent('refresh-data', {
            bubbles: true,
            composed: true
          }));
        }, 20000);
        
        // Keep health checking status visible for 25 seconds total
        setTimeout(() => {
          this.healthCheckingModules = this.healthCheckingModules.filter(m => m !== event.module);
          this.deploymentStatuses.delete(event.module);
          this.requestUpdate();
          
          // Final refresh to ensure we have latest status
          this.dispatchEvent(new CustomEvent('refresh-data', {
            bubbles: true,
            composed: true
          }));
        }, 25000);  // Show health checking for 25 seconds
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
        // Clear health checking state if module just registered
        this.healthCheckingModules = this.healthCheckingModules.filter(m => m !== event.module);
        this.deploymentStatuses.delete(event.module);
        
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
        
      case 'module_undeploying':
        // Add to undeploying list and remove from deployed
        this.undeployingModules = [...(this.undeployingModules || []), event.module];
        this.deploymentStatuses.set(event.module, event.message);
        this.requestUpdate();
        break;
        
      case 'module_undeployed':
        // Keep showing gravestone for a bit
        this.deploymentStatuses.set(event.module, 'Module undeployed');
        this.requestUpdate();
        
        // Remove after 5 seconds (give time for the fade animation)
        setTimeout(() => {
          this.undeployingModules = this.undeployingModules.filter(m => m !== event.module);
          this.deploymentStatuses.delete(event.module);
          this.requestUpdate();
          
          // Fire refresh event to update module list
          this.dispatchEvent(new CustomEvent('refresh-data', {
            bubbles: true,
            composed: true
          }));
        }, 5000);
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
    this.cleaningZombies = true;
    const event = new CustomEvent('cleanup-zombies', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
    
    // Reset button state after 2 seconds
    setTimeout(() => {
      this.cleaningZombies = false;
    }, 2000);
  }

  refreshData() {
    const event = new CustomEvent('refresh-data', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  async redeployOrphanedModule(containerId, moduleName) {
    // Add to registering list
    this.registeringModules = [...this.registeringModules, containerId];
    this.requestUpdate();

    try {
      const response = await fetch(`/api/v1/module-management/orphaned/${containerId}/redeploy`, {
        method: 'POST'
      });

      if (response.ok) {
        const result = await response.json();
        
        // Show success toast
        this.dispatchEvent(new CustomEvent('show-toast', {
          detail: { message: `Started redeployment for ${moduleName}`, type: 'success' },
          bubbles: true,
          composed: true
        }));

        // Remove from orphaned list
        this.orphanedModules = this.orphanedModules.filter(m => (m.container_id || m.containerId) !== containerId);
        
        // Refresh data after a moment to see the redeployed module
        setTimeout(() => {
          this.refreshData();
        }, 3000);
      } else {
        throw new Error('Redeployment failed');
      }
    } catch (error) {
      console.error('Failed to redeploy orphaned module:', error);
      this.dispatchEvent(new CustomEvent('show-toast', {
        detail: { message: `Failed to redeploy ${moduleName}`, type: 'error' },
        bubbles: true,
        composed: true
      }));
    } finally {
      // Remove from registering list
      this.registeringModules = this.registeringModules.filter(id => id !== containerId);
      this.requestUpdate();
    }
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
          <button class="action-button danger" @click=${this.cleanupZombies} style="padding: 0; border: none; background: none;">
            <img 
              src="${this.cleaningZombies ? '/clean-zombies-pressed.svg' : '/clean-zombies-unpressed.svg'}" 
              alt="Cleanup Zombies"
              style="height: 40px; cursor: pointer;"
            >
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

      ${this.orphanedModules && this.orphanedModules.length > 0 && this.isDevMode ? html`
        <div class="orphaned-section">
          <div class="orphaned-header">
            <svg class="orphaned-icon" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-4h2v2h-2zm0-8h2v6h-2z"/>
            </svg>
            <h3 class="orphaned-title">Orphaned Modules</h3>
          </div>
          <p class="orphaned-description">
            These modules are running in Docker but not registered in the pipeline. 
            You can re-register them to make them available again.
          </p>
          <div class="orphaned-modules-list">
            ${this.orphanedModules.map(module => html`
              <div class="orphaned-module-card">
                <div class="orphaned-module-info">
                  <div class="orphaned-module-name">
                    ${module.module_name || module.moduleName}
                    <span class="container-type-badge ${module.container_type || module.containerType || 'module'}">
                      ${module.container_type || module.containerType || 'module'}
                    </span>
                  </div>
                  <div class="orphaned-module-details">
                    Container: ${module.container_name || module.containerName}<br>
                    Port: ${module.port || 'Unknown'}<br>
                    Instance: #${module.instance}
                  </div>
                </div>
                <button 
                  class="register-button ${this.registeringModules.includes(module.containerId) ? 'registering' : ''}"
                  ?disabled=${this.registeringModules.includes(module.container_id || module.containerId)}
                  @click=${() => this.redeployOrphanedModule(module.container_id || module.containerId, module.module_name || module.moduleName)}
                >
                  ${this.registeringModules.includes(module.container_id || module.containerId) ? html`
                    <svg class="button-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="0">
                        <animate attributeName="stroke-dashoffset" from="0" to="-60" dur="2s" repeatCount="indefinite"/>
                      </circle>
                    </svg>
                    Redeploying...
                  ` : html`
                    <svg class="button-icon" viewBox="0 0 20 20" fill="currentColor">
                      <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
                    </svg>
                    Re-deploy
                  `}
                </button>
              </div>
            `)}
          </div>
        </div>
      ` : ''}

      <div class="service-section">
        <div class="section-header">
          <svg class="section-icon" viewBox="0 0 457 512.53" fill="currentColor">
            <path d="m297.8 173.62 15.62 15.25c4.11 4.02 4.19 10.66.18 14.78l-12.29 12.57a82.424 82.424 0 0 1 7.96 20.49l16.11-.2c5.74-.07 10.5 4.58 10.57 10.32l.25 21.83c.07 5.75-4.57 10.51-10.32 10.57l-17.57.21a82.532 82.532 0 0 1-8.87 20.09l11.53 11.26c4.11 4.02 4.19 10.67.18 14.77l-15.25 15.63c-4.02 4.11-10.67 4.19-14.78.17l-12.58-12.28a82.505 82.505 0 0 1-20.48 7.96l.19 16.1c.07 5.75-4.58 10.51-10.32 10.58l-21.83.25c-5.74.07-10.5-4.57-10.57-10.32l-.21-17.57a83.289 83.289 0 0 1-20.09-8.88l-11.26 11.54c-4.02 4.11-10.67 4.19-14.78.17l-15.61-15.25c-4.12-4.02-4.2-10.66-.18-14.78l12.28-12.57c-3.54-6.44-6.24-13.34-7.96-20.48l-16.11.19c-5.74.07-10.5-4.58-10.57-10.32l-.25-21.83c-.07-5.74 4.57-10.5 10.32-10.57l17.57-.21a83.765 83.765 0 0 1 8.87-20.1l-11.53-11.26c-4.11-4.02-4.18-10.66-.17-14.77l15.25-15.62c4.01-4.11 10.66-4.19 14.77-.17l12.58 12.28a82.503 82.503 0 0 1 20.48-7.96l-.19-16.1c-.07-5.74 4.58-10.5 10.33-10.57l21.82-.26c5.75-.07 10.5 4.57 10.57 10.32l.21 17.57a83.224 83.224 0 0 1 20.11 8.87l11.25-11.53c4.02-4.11 10.67-4.18 14.78-.17h-.01zm-81.6-57.98V91.29a46.446 46.446 0 0 1-20.97-12.07c-8.4-8.44-13.6-20.05-13.6-32.82 0-12.75 5.19-24.35 13.6-32.76l.04-.04c8.41-8.4 20-13.6 32.76-13.6 12.76 0 24.36 5.2 32.77 13.6l.04.04c8.4 8.41 13.6 20.01 13.6 32.76 0 12.77-5.2 24.38-13.62 32.79a46.113 46.113 0 0 1-22.35 12.45v23.65c-6.59.03-13.21.17-19.8.25-.83.01-1.65.04-2.47.1zm22.88 305.41c8.35 2.04 15.82 6.37 21.72 12.26l.04.05c8.4 8.41 13.6 20 13.6 32.76 0 12.76-5.2 24.35-13.6 32.76l-.04.05c-8.41 8.4-20.01 13.6-32.77 13.6-12.76 0-24.35-5.2-32.76-13.6l-.04-.05c-8.41-8.41-13.6-20-13.6-32.76 0-12.76 5.19-24.35 13.6-32.76l.04-.05c5.86-5.85 13.27-10.15 21.54-12.22v-23.84c7.16-.01 14.35-.18 21.5-.26l.77-.02v24.08zm6.02 27.96c-4.33-4.33-10.37-7.02-17.07-7.02-6.69 0-12.73 2.69-17.06 7.02l-.05.04c-4.33 4.34-7.02 10.37-7.02 17.07 0 6.7 2.69 12.73 7.02 17.07l.05.04c4.33 4.33 10.37 7.02 17.06 7.02 6.7 0 12.74-2.69 17.07-7.02l.04-.04c4.34-4.34 7.03-10.37 7.03-17.07 0-6.7-2.69-12.73-7.03-17.07l-.04-.04zm0-419.71c-4.33-4.34-10.37-7.03-17.07-7.03-6.69 0-12.73 2.69-17.06 7.03l-.05.04c-4.33 4.33-7.02 10.37-7.02 17.06 0 6.7 2.69 12.74 7.02 17.08 4.41 4.36 10.45 7.06 17.11 7.06 6.67 0 12.71-2.7 17.07-7.06 4.38-4.34 7.07-10.38 7.07-17.08 0-6.69-2.69-12.73-7.03-17.06l-.04-.04zm165.49 285.36c12.76 0 24.36 5.2 32.77 13.6l.04.04c8.4 8.41 13.6 20.01 13.6 32.77 0 12.76-5.2 24.35-13.6 32.76l-.04.05c-8.41 8.4-20.01 13.59-32.77 13.59-12.76 0-24.37-5.2-32.78-13.62-8.42-8.37-13.63-19.98-13.63-32.78 0-4.68.7-9.2 1.99-13.46l-22.02-12.72c2.22-5.46 3.3-11.3 3.23-17.13-.02-2.34-.24-4.67-.63-6.97l30.75 17.75.28-.28c8.39-8.39 20.01-13.6 32.81-13.6zm17.07 29.3c-4.33-4.34-10.37-7.03-17.07-7.03-6.66 0-12.71 2.71-17.07 7.07a24.07 24.07 0 0 0-7.06 17.07c0 6.67 2.7 12.7 7.06 17.06 4.34 4.38 10.38 7.07 17.07 7.07 6.7 0 12.74-2.69 17.07-7.02l.04-.05c4.34-4.33 7.03-10.36 7.03-17.06 0-6.7-2.69-12.74-7.03-17.07l-.04-.04zm-381.25-29.3c12.54 0 23.94 5 32.29 13.09l28.75-16.61c-.21 1.85-.3 3.7-.28 5.56.06 6.15 1.42 12.27 4.03 17.93l-20.69 11.95c1.5 4.56 2.31 9.43 2.31 14.49 0 12.8-5.21 24.41-13.6 32.81-8.44 8.39-20.05 13.59-32.81 13.59-12.76 0-24.36-5.19-32.77-13.59l-.04-.05c-8.4-8.41-13.6-20-13.6-32.76 0-12.77 5.2-24.37 13.62-32.79 8.37-8.41 19.98-13.62 32.79-13.62zM63.48 344a24.096 24.096 0 0 0-17.07-7.07c-6.67 0-12.71 2.71-17.07 7.07-4.38 4.33-7.07 10.37-7.07 17.07 0 6.7 2.69 12.73 7.03 17.06l.04.05c4.33 4.33 10.37 7.02 17.07 7.02 6.69 0 12.73-2.69 17.07-7.02 4.36-4.41 7.06-10.44 7.06-17.11s-2.7-12.71-7.06-17.07zm347.11-239.2c12.81 0 24.42 5.2 32.82 13.6 8.39 8.39 13.59 20 13.59 32.81 0 12.76-5.2 24.36-13.6 32.77l-.04.04c-8.41 8.4-20.01 13.6-32.77 13.6-12.8 0-24.42-5.21-32.81-13.6l-.59-.61-27.45 15.87c.08-1.15.11-2.3.09-3.44a44.001 44.001 0 0 0-4.79-19.5l21-12.14c-1.2-4.12-1.86-8.48-1.86-12.99 0-12.76 5.2-24.35 13.6-32.76l.05-.05c8.41-8.4 20-13.6 32.76-13.6zm17.08 29.34a24.094 24.094 0 0 0-17.08-7.06c-6.7 0-12.73 2.69-17.07 7.02l-.04.04c-4.33 4.34-7.02 10.37-7.02 17.07 0 6.66 2.7 12.71 7.06 17.07a24.096 24.096 0 0 0 17.07 7.07c6.7 0 12.74-2.69 17.07-7.03l.04-.04c4.34-4.33 7.03-10.37 7.03-17.07 0-6.67-2.71-12.72-7.06-17.07zM46.41 104.8c12.76 0 24.35 5.2 32.76 13.6l.05.05c8.4 8.41 13.6 20 13.6 32.76 0 3.76-.46 7.42-1.3 10.92l22.22 13.47a43.973 43.973 0 0 0-3.85 23.58l-28.62-17.35c-.66.75-1.35 1.49-2.05 2.19-8.39 8.39-20.01 13.6-32.81 13.6-12.81 0-24.42-5.21-32.81-13.6C5.2 175.58 0 163.98 0 151.21c0-12.76 5.2-24.35 13.6-32.76l.04-.05c8.41-8.4 20.01-13.6 32.77-13.6zm17.07 29.3c-4.34-4.33-10.37-7.02-17.07-7.02-6.7 0-12.74 2.69-17.07 7.02l-.04.04c-4.34 4.34-7.03 10.37-7.03 17.07 0 6.7 2.69 12.74 7.03 17.07 4.4 4.36 10.44 7.07 17.11 7.07 6.66 0 12.71-2.71 17.07-7.07a24.088 24.088 0 0 0 7.06-17.07c0-6.7-2.69-12.73-7.02-17.07l-.04-.04zm164.5 79.25c23.7-.28 43.15 18.71 43.43 42.4.29 23.7-18.71 43.15-42.41 43.43-23.69.29-43.14-18.71-43.42-42.4-.28-23.7 18.71-43.15 42.4-43.43z"/>
          </svg>
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
          <svg class="section-icon" viewBox="0 0 122.87 122.88" fill="currentColor">
            <path d="M33.24,40.86l27.67-9.21c0.33-0.11,0.68-0.1,0.98,0v0l28.03,9.6c0.69,0.23,1.11,0.9,1.04,1.6 c0.01,0.03,0.01,0.07,0.01,0.11v32.6h-0.01c0,0.56-0.31,1.11-0.85,1.38L62.28,91.08c-0.23,0.14-0.51,0.22-0.8,0.22 c-0.31,0-0.6-0.09-0.84-0.25l-27.9-14.55c-0.53-0.28-0.83-0.81-0.83-1.37h0V42.4C31.9,41.61,32.48,40.97,33.24,40.86L33.24,40.86 L33.24,40.86z M24.28,21.66l8.46,8.46c0.74,0.74,0.74,1.93,0,2.67c-0.73,0.73-1.93,0.73-2.66,0l-8.4-8.4l0.23,5.56 c0,0.05,0,0.11-0.02,0.16c-0.13,0.42-0.4,0.78-0.74,1.03c-0.34,0.25-0.75,0.4-1.2,0.4c-0.56,0.01-1.08-0.22-1.45-0.59 c-0.38-0.37-0.61-0.88-0.62-1.45c-0.16-3.2-0.49-6.78-0.49-9.93c0-0.64,0.22-1.18,0.61-1.56c0.38-0.37,0.9-0.59,1.52-0.6 c2.68-0.1,7.21,0.26,10,0.46c0.56,0.01,1.07,0.23,1.43,0.6c0.36,0.36,0.59,0.86,0.61,1.41v0.05c0,0.56-0.23,1.08-0.6,1.45 c-0.36,0.36-0.86,0.59-1.41,0.6l-0.04,0l0,0c-1.7,0-3.01-0.12-4.31-0.24L24.28,21.66L24.28,21.66z M7.04,59.58H19 c1.04,0,1.88,0.84,1.88,1.88s-0.84,1.88-1.88,1.88H7.12l4.1,3.77c0.04,0.04,0.07,0.08,0.1,0.13c0.2,0.39,0.27,0.83,0.2,1.25 c-0.06,0.41-0.25,0.81-0.57,1.13c-0.39,0.4-0.92,0.61-1.44,0.61c-0.53,0-1.06-0.19-1.46-0.59c-2.37-2.15-5.14-4.45-7.37-6.68 C0.22,62.52,0,61.99,0,61.45c0-0.53,0.22-1.05,0.65-1.49c1.82-1.97,5.29-4.91,7.4-6.74c0.4-0.39,0.92-0.59,1.44-0.59 c0.51,0,1.02,0.19,1.42,0.56l0.04,0.04c0.4,0.4,0.6,0.93,0.6,1.45c0,0.51-0.19,1.02-0.57,1.42l-0.02,0.03l0,0 c-1.2,1.21-2.21,2.04-3.22,2.87L7.04,59.58L7.04,59.58z M21.66,98.6l8.46-8.46c0.73-0.73,1.93-0.73,2.66,0 c0.74,0.74,0.74,1.93,0,2.67l-8.4,8.4l5.56-0.23c0.05,0,0.11,0.01,0.16,0.02c0.42,0.14,0.78,0.4,1.03,0.74 c0.25,0.34,0.4,0.75,0.4,1.2c0,0.56-0.22,1.08-0.59,1.45c-0.37,0.38-0.88,0.61-1.45,0.62c-3.2,0.16-6.78,0.49-9.94,0.49 c-0.64,0-1.18-0.22-1.56-0.6c-0.37-0.38-0.59-0.9-0.6-1.52c-0.11-2.68,0.26-7.21,0.46-10c0.01-0.56,0.23-1.07,0.6-1.43 c0.36-0.36,0.86-0.59,1.4-0.61h0.05c0.56,0,1.08,0.23,1.45,0.6c0.36,0.36,0.59,0.86,0.61,1.41l0,0.03l0,0 c0.01,1.71-0.12,3.01-0.24,4.31L21.66,98.6L21.66,98.6z M59.58,115.83v-11.96c0-1.04,0.84-1.88,1.88-1.88 c1.04,0,1.88,0.84,1.88,1.88v11.88l3.77-4.1c0.04-0.04,0.08-0.07,0.13-0.1c0.39-0.2,0.83-0.27,1.25-0.2 c0.41,0.06,0.81,0.25,1.13,0.57c0.4,0.39,0.61,0.92,0.61,1.45c0,0.53-0.19,1.06-0.59,1.46c-2.15,2.37-4.45,5.14-6.68,7.37 c-0.46,0.45-0.99,0.68-1.53,0.68c-0.53,0-1.05-0.22-1.49-0.65c-1.97-1.82-4.91-5.28-6.74-7.4c-0.39-0.4-0.59-0.92-0.59-1.44 c0-0.51,0.19-1.03,0.56-1.42l0.04-0.04c0.4-0.4,0.93-0.6,1.45-0.6c0.51,0,1.02,0.19,1.42,0.57l0.02,0.02l0,0 c1.21,1.2,2.04,2.21,2.87,3.22L59.58,115.83L59.58,115.83z M98.6,101.22l-8.46-8.46c-0.74-0.74-0.74-1.93,0-2.67 c0.73-0.73,1.93-0.73,2.66,0l8.4,8.4l-0.23-5.56c0-0.05,0-0.11,0.02-0.16c0.13-0.42,0.4-0.78,0.74-1.03c0.34-0.25,0.75-0.4,1.2-0.4 c0.56-0.01,1.08,0.22,1.45,0.59c0.38,0.37,0.61,0.88,0.62,1.45c0.16,3.2,0.49,6.78,0.49,9.94c0,0.64-0.22,1.18-0.61,1.56 c-0.38,0.37-0.9,0.59-1.52,0.6c-2.68,0.1-7.21-0.26-10-0.46c-0.56-0.01-1.07-0.23-1.43-0.6c-0.36-0.36-0.59-0.86-0.61-1.41v-0.05 c0-0.56,0.23-1.08,0.6-1.45c0.36-0.36,0.86-0.59,1.41-0.61l0.04,0l0,0c1.71-0.01,3.01,0.12,4.3,0.24L98.6,101.22L98.6,101.22z M115.84,63.29h-11.96c-1.04,0-1.89-0.84-1.89-1.88c0-1.04,0.85-1.88,1.89-1.88h11.88l-4.1-3.77c-0.04-0.04-0.07-0.08-0.1-0.13 c-0.2-0.39-0.27-0.83-0.2-1.25c0.06-0.41,0.25-0.81,0.57-1.13c0.4-0.4,0.92-0.61,1.45-0.61c0.53,0,1.06,0.19,1.46,0.59 c2.37,2.15,5.14,4.45,7.37,6.68c0.45,0.46,0.68,0.99,0.67,1.53c0,0.53-0.22,1.05-0.65,1.49c-1.82,1.97-5.29,4.91-7.4,6.74 c-0.4,0.39-0.92,0.59-1.44,0.59c-0.51,0-1.03-0.19-1.42-0.56l-0.04-0.04c-0.4-0.4-0.6-0.93-0.6-1.45c0-0.51,0.19-1.03,0.57-1.42 l0.02-0.03l0,0c1.2-1.21,2.21-2.04,3.22-2.87L115.84,63.29L115.84,63.29z M101.21,24.28l-8.46,8.46c-0.73,0.73-1.93,0.73-2.66,0 c-0.74-0.74-0.74-1.93,0-2.66l8.4-8.4l-5.56,0.23c-0.05,0-0.11-0.01-0.16-0.02c-0.42-0.14-0.78-0.4-1.03-0.74 c-0.25-0.34-0.4-0.75-0.4-1.2c0-0.56,0.22-1.08,0.59-1.45c0.37-0.38,0.88-0.61,1.45-0.62c3.2-0.16,6.78-0.49,9.94-0.49 c0.64,0,1.18,0.22,1.56,0.6c0.37,0.38,0.59,0.9,0.6,1.52c0.11,2.68-0.26,7.21-0.46,10c-0.01,0.56-0.23,1.07-0.6,1.44 c-0.36,0.36-0.86,0.59-1.41,0.61h-0.05c-0.56,0-1.08-0.23-1.45-0.6c-0.36-0.36-0.59-0.86-0.61-1.41l0-0.03l0,0 c0-1.71,0.12-3.01,0.24-4.31L101.21,24.28L101.21,24.28z M63.29,7.04V19c0,1.04-0.84,1.88-1.88,1.88c-1.04,0-1.89-0.84-1.89-1.88 V7.13l-3.76,4.09c-0.04,0.04-0.08,0.07-0.13,0.1c-0.39,0.2-0.83,0.27-1.25,0.2c-0.41-0.06-0.81-0.25-1.13-0.57 c-0.4-0.39-0.61-0.92-0.61-1.44c0-0.53,0.19-1.06,0.59-1.46c2.15-2.37,4.45-5.14,6.68-7.37C60.35,0.22,60.89,0,61.43,0 c0.53,0,1.05,0.22,1.49,0.65c1.97,1.82,4.91,5.28,6.74,7.4c0.39,0.4,0.59,0.92,0.59,1.44c0,0.51-0.19,1.02-0.56,1.42l-0.04,0.04 c-0.4,0.4-0.93,0.6-1.45,0.6c-0.51,0-1.02-0.19-1.42-0.57l-0.03-0.02l0,0c-1.21-1.2-2.04-2.21-2.87-3.22L63.29,7.04L63.29,7.04z M39.36,64.75c0-0.59,0.48-1.08,1.08-1.08c0.59,0,1.08,0.48,1.08,1.08v4.39c0,0.03,0,0.07-0.01,0.11c0,0.15,0.02,0.27,0.05,0.37 c0.02,0.03,0.03,0.06,0.06,0.08l2.69,1.25c0.54,0.25,0.77,0.89,0.53,1.43c-0.25,0.54-0.88,0.77-1.42,0.53l-2.75-1.28 c-0.05-0.02-0.1-0.04-0.14-0.08c-0.44-0.28-0.75-0.65-0.94-1.11c-0.15-0.37-0.22-0.78-0.21-1.22v-0.07L39.36,64.75L39.36,64.75 L39.36,64.75z M59.93,87.21V56.02L35,44.72v29.48L59.93,87.21L59.93,87.21L59.93,87.21z M87.86,45.09L63.03,56.04v31.2l24.83-12.62 V45.09L87.86,45.09L87.86,45.09z M61.38,34.74l-23.57,7.85l23.68,10.74L85.17,42.9L61.38,34.74L61.38,34.74L61.38,34.74z"/>
          </svg>
          <h2 class="section-title">Module Services</h2>
          <span class="section-count">${moduleServices.length}</span>
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
                <svg class="rocket-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <style>
                    .rocket-body {
                      animation: launch 1.5s ease-in-out infinite;
                    }
                    .flame {
                      animation: flicker 0.3s ease-in-out infinite alternate;
                    }
                    .smoke {
                      animation: puff 1.5s ease-out infinite;
                      opacity: 0;
                    }
                    @keyframes launch {
                      0% { transform: translateY(2px); }
                      50% { transform: translateY(-2px); }
                      100% { transform: translateY(2px); }
                    }
                    @keyframes flicker {
                      0% { transform: scaleY(1); }
                      100% { transform: scaleY(1.2); }
                    }
                    @keyframes puff {
                      0% { transform: scale(0.8) translateY(0); opacity: 0; }
                      50% { opacity: 0.5; }
                      100% { transform: scale(1.3) translateY(4px); opacity: 0; }
                    }
                  </style>
                  <!-- Rocket body -->
                  <path d="M12 2l-2 7v6l2 1 2-1v-6l-2-7z" class="rocket-body"/>
                  <!-- Rocket fins -->
                  <path d="M8 14l-2 2v3h2m8-5l2 2v3h-2" class="rocket-body"/>
                  <!-- Rocket window -->
                  <circle cx="12" cy="8" r="1.5" class="rocket-body"/>
                  <!-- Flames -->
                  <path d="M10 16l-1 3m4-3l1 3m-2-3l0 3" class="flame"/>
                  <!-- Smoke puffs -->
                  <circle cx="9" cy="20" r="1" class="smoke"/>
                  <circle cx="12" cy="21" r="1.5" class="smoke" style="animation-delay: 0.5s;"/>
                  <circle cx="15" cy="20" r="1" class="smoke" style="animation-delay: 1s;"/>
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

        ${this.undeployingModules && this.undeployingModules.length > 0 ? html`
          ${this.undeployingModules.map(moduleName => html`
            <div class="undeploying-card">
              <div class="undeploying-header">
                <img class="gravestone-icon" src="/tombstone.svg" alt="Tombstone" style="width: 48px; height: 48px; opacity: 0.7;">
                <div>
                  <div class="undeploying-title">Undeploying ${moduleName}...</div>
                  <div class="undeploying-subtitle">${this.deploymentStatuses.get(moduleName) || 'Removing module and containers'}</div>
                </div>
              </div>
            </div>
          `)}
        ` : ''}

        ${this.healthCheckingModules && this.healthCheckingModules.length > 0 ? html`
          ${this.healthCheckingModules.map(moduleName => html`
            <div class="health-checking-card">
              <div class="health-checking-header">
                <svg class="health-cross-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <style>
                    .helicopter-body {
                      animation: hover 2s ease-in-out infinite;
                    }
                    .rotor {
                      animation: spin 0.3s linear infinite;
                      transform-origin: 12px 5px;
                    }
                    .cross-pulse {
                      animation: pulse-cross 1.5s ease-in-out infinite;
                    }
                    @keyframes hover {
                      0%, 100% { transform: translateY(0); }
                      50% { transform: translateY(-3px); }
                    }
                    @keyframes spin {
                      from { transform: rotate(0deg); }
                      to { transform: rotate(360deg); }
                    }
                    @keyframes pulse-cross {
                      0%, 100% { opacity: 1; }
                      50% { opacity: 0.5; }
                    }
                  </style>
                  <!-- Main rotor -->
                  <path d="M2 5h20" class="rotor"/>
                  <!-- Rotor mast -->
                  <path d="M12 5v3" class="helicopter-body"/>
                  <!-- Helicopter body -->
                  <path d="M5 11h14c1 0 2 1 2 2v4c0 1-1 2-2 2H5c-1 0-2-1-2-2v-4c0-1 1-2 2-2z" class="helicopter-body"/>
                  <!-- Cockpit window -->
                  <path d="M5 13h4" class="helicopter-body"/>
                  <!-- Landing skids -->
                  <path d="M6 19v2m12-2v2" class="helicopter-body"/>
                  <path d="M4 21h6m4 0h6" class="helicopter-body"/>
                  <!-- Medical cross -->
                  <rect x="11" y="13" width="4" height="4" rx="0.5" class="helicopter-body"/>
                  <path d="M13 14v2m-1-1h2" class="cross-pulse" stroke-width="1.5"/>
                  <!-- Tail -->
                  <path d="M19 13l3-2v-1l-3-2" class="helicopter-body"/>
                  <!-- Tail rotor -->
                  <circle cx="22" cy="10" r="1" class="rotor"/>
                </svg>
                <div>
                  <div class="health-checking-title">${moduleName}</div>
                  <div class="health-checking-subtitle">${this.deploymentStatuses.get(moduleName) || 'Waiting for module to be healthy...'}</div>
                </div>
              </div>
            </div>
          `)}
        ` : ''}

        ${moduleServices.length === 0 && 
          (!this.deployingModules || this.deployingModules.length === 0) && 
          (!this.undeployingModules || this.undeployingModules.length === 0) &&
          (!this.healthCheckingModules || this.healthCheckingModules.length === 0) ? html`
          <div class="empty-state">No module services found</div>
        ` : html`
          <div class="instances-grid">
            ${moduleServices
              .filter(module => {
                // Don't show modules that are currently being deployed or health-checked
                const moduleName = module.module_name?.toLowerCase() || '';
                return !this.deployingModules?.some(m => m.toLowerCase() === moduleName) &&
                       !this.healthCheckingModules?.some(m => m.toLowerCase() === moduleName) &&
                       !this.undeployingModules?.some(m => m.toLowerCase() === moduleName);
              })
              .map(module => html`
                <service-box 
                  .service=${{ name: module.service_name, type: 'module' }} 
                  .instance=${module.instances[0]}
                  .instanceCount=${module.instances.length}
                  .allInstances=${module.instances}
                  .isDevMode=${this.isDevMode}>
                </service-box>
              `)}
          </div>
        `}
      </div>

      <!-- Observability Services Section -->
      <div class="service-section">
        <div class="section-header">
          <svg class="section-icon" viewBox="0 0 20 20" fill="currentColor">
            <path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/>
            <path fill-rule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd"/>
          </svg>
          <h2 class="section-title">Observability Services</h2>
          <span class="section-count">2</span>
        </div>
        
        <div class="instances-grid">
          <!-- Grafana Card -->
          <service-box
            .service=${{ 
              name: 'grafana',
              type: 'observability',
              iconUrl: '/grafana.svg'
            }}
            .instance=${{ 
              id: 'grafana-1',
              service_name: 'grafana',
              name: 'Grafana',
              address: 'localhost',
              port: 3000,
              healthy: true,
              metadata: {
                description: 'Metrics visualization and dashboards',
                url: 'http://localhost:3000'
              }
            }}
            .instanceCount=${1}
            .allInstances=${[]}>
          </service-box>
          
          <!-- Prometheus Card -->
          <service-box
            .service=${{ 
              name: 'prometheus',
              type: 'observability',
              iconUrl: '/prometheus.svg'
            }}
            .instance=${{ 
              id: 'prometheus-1',
              service_name: 'prometheus',
              name: 'Prometheus',
              address: 'localhost',
              port: 9090,
              healthy: true,
              metadata: {
                description: 'Metrics collection and storage',
                url: 'http://localhost:9090'
              }
            }}
            .instanceCount=${1}
            .allInstances=${[]}>
          </service-box>
        </div>
      </div>
    `;
  }
}

customElements.define('dashboard-grid', DashboardGrid);