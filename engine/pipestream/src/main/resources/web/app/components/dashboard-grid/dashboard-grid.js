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
    healthCheckingModules: { type: Array }
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
                <svg class="gravestone-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <style>
                    .tombstone {
                      animation: fade 2s ease-in-out infinite;
                    }
                    .rip-text {
                      animation: fade 2s ease-in-out infinite;
                      animation-delay: -0.3s;
                    }
                    @keyframes fade {
                      0% { opacity: 1; }
                      50% { opacity: 0.3; }
                      100% { opacity: 1; }
                    }
                  </style>
                  <!-- Tombstone shape -->
                  <path d="M6 22h12v-8c0-3.3-2.7-6-6-6s-6 2.7-6 6v8z" class="tombstone"/>
                  <!-- Ground line -->
                  <path d="M3 22h18" class="tombstone"/>
                  <!-- RIP text -->
                  <path d="M9 13h2m-2 3h2" class="rip-text" stroke-width="1.5"/>
                  <path d="M11 13v3" class="rip-text" stroke-width="1.5"/>
                  <path d="M13 13h2c.5 0 1 .5 1 1s-.5 1-1 1h-2v1" class="rip-text" stroke-width="1.5"/>
                </svg>
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
        ` : moduleServices
          .filter(module => {
            // Don't show modules that are currently being deployed or health-checked
            const moduleName = module.module_name?.toLowerCase() || '';
            return !this.deployingModules?.some(m => m.toLowerCase() === moduleName) &&
                   !this.healthCheckingModules?.some(m => m.toLowerCase() === moduleName) &&
                   !this.undeployingModules?.some(m => m.toLowerCase() === moduleName);
          })
          .map(module => html`
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