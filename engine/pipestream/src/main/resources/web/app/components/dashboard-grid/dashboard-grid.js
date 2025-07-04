import { LitElement, html, css } from 'lit';
import '../service-box/service-box.js';

export class DashboardGrid extends LitElement {
  static properties = {
    baseServices: { type: Array },
    moduleServices: { type: Array },
    statistics: { type: Object }
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
  `;

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
            .instance=${group.primaryInstance}>
          </service-box>
        `;
      } else {
        // Multiple instances, render with stack effect
        return html`
          <service-box 
            .service=${group.service} 
            .instance=${group.primaryInstance}
            .instanceCount=${count}
            .allInstances=${group.instances}>
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
          <h2 class="section-title">Module Services</h2>
          <span class="section-count">${moduleServices.length}</span>
        </div>

        ${moduleServices.length === 0 ? html`
          <div class="empty-state">No module services found</div>
        ` : moduleServices.map(module => html`
          <div class="module-service">
            <div class="module-header">
              <div class="module-name">${module.moduleName}</div>
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
                  .service=${{ name: module.serviceName, type: 'module' }} 
                  .instance=${instance}>
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