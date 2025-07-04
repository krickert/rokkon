import { LitElement, html, css } from 'lit';

export class ServiceBox extends LitElement {
  static properties = {
    service: { type: Object },
    instance: { type: Object },
    expanded: { type: Boolean },
    instanceCount: { type: Number },
    allInstances: { type: Array }
  };

  static styles = css`
    :host {
      display: block;
      position: relative;
    }

    .stack-container {
      position: relative;
    }

    .stack-shadow {
      position: absolute;
      top: 4px;
      left: 4px;
      right: -4px;
      bottom: -4px;
      background: #e0e0e0;
      border-radius: 8px;
      z-index: -1;
    }

    .stack-shadow.double {
      box-shadow: 4px 4px 0 #d0d0d0;
    }

    .service-box {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 12px;
      transition: all 0.2s ease;
      cursor: pointer;
      position: relative;
    }

    .service-box:hover {
      border-color: #1976d2;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      transform: translateY(-1px);
    }

    .service-box.healthy {
      border-left: 4px solid #4caf50;
    }

    .service-box.unhealthy {
      border-left: 4px solid #f44336;
    }

    .service-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .service-name {
      font-weight: 600;
      font-size: 16px;
      color: #333;
      display: flex;
      align-items: center;
    }

    .service-id {
      font-size: 11px;
      color: #999;
      font-family: monospace;
      margin-top: 2px;
    }

    .status-badge {
      padding: 4px 8px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
    }

    .status-badge.healthy {
      background: #e8f5e9;
      color: #2e7d32;
    }

    .status-badge.unhealthy {
      background: #ffebee;
      color: #c62828;
    }

    .service-details {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      font-size: 14px;
      color: #666;
    }

    .detail-item {
      display: flex;
      align-items: center;
      gap: 4px;
      white-space: nowrap;
    }

    .detail-icon {
      width: 16px;
      height: 16px;
      opacity: 0.5;
    }

    .duplicate-warning {
      position: absolute;
      top: 8px;
      right: 8px;
      background: #fff3cd;
      color: #856404;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 11px;
      border: 1px solid #ffeeba;
    }

    .metadata {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #eee;
      font-size: 13px;
      color: #666;
      display: none;
    }

    .metadata.show {
      display: block;
    }

    .metadata-item {
      display: flex;
      gap: 8px;
      margin-bottom: 4px;
    }

    .metadata-key {
      font-weight: 500;
      min-width: 100px;
    }

    .service-icon {
      width: 20px;
      height: 20px;
      margin-right: 8px;
      color: #1976d2;
      flex-shrink: 0;
    }

    .instance-count {
      position: absolute;
      top: -8px;
      right: -8px;
      background: #1976d2;
      color: white;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 600;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
      z-index: 10;
      cursor: pointer;
    }

    .instance-tooltip {
      position: absolute;
      top: 20px;
      right: -10px;
      background: rgba(0, 0, 0, 0.9);
      color: white;
      padding: 8px 12px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: normal;
      white-space: nowrap;
      opacity: 0;
      visibility: hidden;
      transition: opacity 0.2s ease;
      z-index: 20;
      max-width: 300px;
    }

    .instance-count:hover .instance-tooltip {
      opacity: 1;
      visibility: visible;
    }

    .tooltip-instance {
      margin-bottom: 4px;
      font-family: monospace;
      font-size: 11px;
    }

    .tooltip-instance:last-child {
      margin-bottom: 0;
    }

    .grpc-services {
      margin-top: 8px;
      padding: 8px;
      background: #f5f5f5;
      border-radius: 4px;
      font-size: 12px;
    }

    .grpc-service {
      margin-bottom: 2px;
      font-family: monospace;
      color: #1976d2;
    }

    .module-actions {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #eee;
      display: flex;
      gap: 8px;
    }

    .module-action {
      padding: 4px 12px;
      border: 1px solid #e0e0e0;
      background: white;
      border-radius: 4px;
      font-size: 12px;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .module-action:hover {
      background: #f5f5f5;
      border-color: #1976d2;
    }

    .module-action.primary {
      background: #1976d2;
      color: white;
      border-color: #1976d2;
    }

    .module-action.primary:hover {
      background: #1565c0;
    }

    .module-action.danger {
      color: #f44336;
      border-color: #f44336;
    }

    .module-action.danger:hover {
      background: #ffebee;
    }

    .module-action:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  `;

  constructor() {
    super();
    this.expanded = false;
  }

  toggleExpanded() {
    this.expanded = !this.expanded;
  }

  getServiceIcon(service) {
    const { name, type } = service;
    
    // Check by name first, then by type
    if (name === 'consul') {
      return html`<svg class="service-icon" fill="currentColor" viewBox="0 0 20 20">
        <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z"/>
      </svg>`;
    }
    
    if (name === 'pipeline-engine' || type === 'orchestrator') {
      return html`<svg class="service-icon" fill="currentColor" viewBox="0 0 20 20">
        <path fill-rule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clip-rule="evenodd"/>
      </svg>`;
    }
    
    if (type === 'module') {
      return html`<svg class="service-icon" fill="currentColor" viewBox="0 0 20 20">
        <path d="M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a2 2 0 012-2h12a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4z"/>
      </svg>`;
    }
    
    // Default service icon
    return html`<svg class="service-icon" fill="currentColor" viewBox="0 0 20 20">
      <path fill-rule="evenodd" d="M2 5a2 2 0 012-2h12a2 2 0 012 2v10a2 2 0 01-2 2H4a2 2 0 01-2-2V5zm3.293 1.293a1 1 0 011.414 0l3 3a1 1 0 010 1.414l-3 3a1 1 0 01-1.414-1.414L7.586 10 5.293 7.707a1 1 0 010-1.414zM11 12a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd"/>
    </svg>`;
  }

  handleModuleAction(action, instance) {
    const event = new CustomEvent('module-action', {
      detail: { action, instance },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  render() {
    const { service, instance, instanceCount = 1, allInstances = [] } = this;
    if (!service || !instance) return html``;

    const isHealthy = instance.healthy;
    const isDuplicate = service.hasDuplicates;
    const hasStack = instanceCount > 1;
    const isModule = service.type === 'module';
    const isRegistered = instance.registered;

    return html`
      <div class="stack-container">
        ${hasStack ? html`
          <div class="stack-shadow ${instanceCount > 2 ? 'double' : ''}"></div>
        ` : ''}
        
        <div class="service-box ${isHealthy ? 'healthy' : 'unhealthy'}" 
             @click=${this.toggleExpanded}>
          
          ${hasStack ? html`
            <div class="instance-count">
              ${instanceCount}
              <div class="instance-tooltip">
                ${allInstances.map(inst => html`
                  <div class="tooltip-instance">
                    ${inst.id} - ${inst.address}:${inst.port}
                  </div>
                `)}
              </div>
            </div>
          ` : ''}
        
        ${isDuplicate ? html`
          <div class="duplicate-warning" title="${service.duplicateReason}">
            Duplicate
          </div>
        ` : ''}

        <div class="service-header">
          <div>
            <div class="service-name">
              ${this.getServiceIcon(service)}
              ${service.name}
            </div>
            <div class="service-id">${instance.id}</div>
          </div>
          <span class="status-badge ${isHealthy ? 'healthy' : 'unhealthy'}">
            ${isHealthy ? 'Healthy' : 'Unhealthy'}
          </span>
        </div>

        <div class="service-details">
          <div class="detail-item">
            <svg class="detail-icon" fill="currentColor" viewBox="0 0 20 20">
              <path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/>
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm0-2a6 6 0 100-12 6 6 0 000 12z"/>
            </svg>
            ${instance.address}:${instance.port}
          </div>
          ${instance.version ? html`
            <div class="detail-item">
              <svg class="detail-icon" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z"/>
              </svg>
              v${instance.version}
            </div>
          ` : ''}
          <div class="detail-item">
            <svg class="detail-icon" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M17.707 9.293a1 1 0 010 1.414l-7 7a1 1 0 01-1.414 0l-7-7A.997.997 0 012 10V5a3 3 0 013-3h5c.256 0 .512.098.707.293l7 7zM5 6a1 1 0 100-2 1 1 0 000 2z"/>
            </svg>
            ${service.type}
          </div>
        </div>

        ${instance.grpc_services && instance.grpc_services.length > 0 ? html`
          <div class="grpc-services">
            <strong>gRPC Services:</strong>
            ${instance.grpc_services.map(svc => html`
              <div class="grpc-service">${svc}</div>
            `)}
          </div>
        ` : ''}

        <div class="metadata ${this.expanded ? 'show' : ''}">
          ${hasStack ? html`
            <div class="metadata-item">
              <span class="metadata-key">Instance IDs:</span>
              <span>${allInstances.map(inst => inst.id).join(', ')}</span>
            </div>
            <hr style="margin: 8px 0; border: none; border-top: 1px solid #eee;">
          ` : ''}
          ${instance.metadata && Object.entries(instance.metadata).map(([key, value]) => html`
            <div class="metadata-item">
              <span class="metadata-key">${key}:</span>
              <span>${value}</span>
            </div>
          `)}
        </div>

        ${isModule && this.expanded ? html`
          <div class="module-actions">
            ${isRegistered ? html`
              ${isHealthy ? html`
                <button 
                  class="module-action"
                  @click=${() => this.handleModuleAction('disable', instance)}>
                  Disable
                </button>
              ` : html`
                <button 
                  class="module-action primary"
                  @click=${() => this.handleModuleAction('enable', instance)}>
                  Enable
                </button>
              `}
              <button 
                class="module-action danger"
                @click=${() => this.handleModuleAction('deregister', instance)}>
                Deregister
              </button>
            ` : html`
              ${isHealthy ? html`
                <button 
                  class="module-action primary"
                  @click=${() => this.handleModuleAction('register', instance)}>
                  Register
                </button>
              ` : html`
                <button class="module-action" disabled>
                  Unhealthy - Cannot Register
                </button>
              `}
            `}
          </div>
        ` : ''}
        </div>
      </div>
    `;
  }
}

customElements.define('service-box', ServiceBox);