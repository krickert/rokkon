import { LitElement, html, css } from 'lit';

export class ServiceBox extends LitElement {
  static properties = {
    service: { type: Object },
    instance: { type: Object },
    expanded: { type: Boolean },
    instanceCount: { type: Number },
    allInstances: { type: Array },
    isDevMode: { type: Boolean }
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

    
    .module-top-actions {
      position: absolute;
      top: -8px;
      right: -8px;
      display: flex;
      gap: 4px;
      z-index: 10;
    }
    
    .top-action {
      background: white;
      border: 1px solid #e0e0e0;
      cursor: pointer;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #666;
      transition: all 0.2s ease;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .top-action:hover {
      background: #f5f5f5;
      color: #333;
      transform: scale(1.1);
    }
    
    .top-action.pause {
      color: #1976d2;
    }
    
    .top-action.pause:hover {
      background: #e3f2fd;
      color: #1565c0;
    }
    
    .top-action.danger:hover {
      background: #ffebee;
      color: #c62828;
    }
    
    .top-action svg {
      width: 14px;
      height: 14px;
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
    
    .grpc-service-methods {
      margin-left: 16px;
      font-size: 10px;
      color: #666;
      display: none;
    }
    
    .grpc-service:hover .grpc-service-methods {
      display: block;
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

    .module-header-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    .header-action {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
      color: #666;
    }

    .header-action:hover {
      background: rgba(0, 0, 0, 0.05);
    }

    .header-action.danger:hover {
      background: #ffebee;
      color: #c62828;
    }

    .header-action.primary:hover {
      background: #e3f2fd;
      color: #1565c0;
    }

    .header-action svg {
      width: 20px;
      height: 20px;
    }

    .dev-only-border {
      border: 2px dashed #ff9800;
      position: relative;
    }

    .dev-only-badge {
      position: absolute;
      top: -10px;
      right: 8px;
      background: #ff9800;
      color: white;
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 600;
      text-transform: uppercase;
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
    const { name, type, icon, iconUrl } = service;
    
    // If custom icon URL is provided, use it
    if (iconUrl) {
      return html`<img class="service-icon" src="${iconUrl}" alt="${name} icon" style="width: 20px; height: 20px;">`;
    }
    
    // If custom icon (inline SVG) is provided, use it
    if (icon) {
      return icon;
    }
    
    // Check by name first, then by type
    if (name === 'consul') {
      return html`<img class="service-icon" src="/consul.svg" alt="Consul icon" style="width: 20px; height: 20px;">`;
    }
    
    if (name === 'pipeline-engine' || type === 'orchestrator') {
      return html`<img class="service-icon" src="/pipeline-engine.svg" alt="Pipeline Engine icon" style="width: 20px; height: 20px;">`;
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

  handleModuleAction(action, instanceOrInstances) {
    // Handle actions on all instances
    if (action === 'disable-all' || action === 'enable-all' || action === 'deregister-all') {
      const baseAction = action.replace('-all', '');
      // Send multiple events for each instance
      instanceOrInstances.forEach(instance => {
        const event = new CustomEvent('module-action', {
          detail: { action: baseAction, instance, service: this.service },
          bubbles: true,
          composed: true
        });
        this.dispatchEvent(event);
      });
    } else {
      // Single instance action
      const event = new CustomEvent('module-action', {
        detail: { action, instance: instanceOrInstances, service: this.service },
        bubbles: true,
        composed: true
      });
      this.dispatchEvent(event);
    }
  }
  
  getServiceMethods(serviceName) {
    // This is a placeholder - in the future, we could get actual methods from metadata
    // For now, return known methods for certain services
    const knownMethods = {
      'TestProcessor': ['Process', 'HealthCheck'],
      'TestHarness': ['RunTest', 'GetResults', 'Configure'],
      'EchoService': ['Echo', 'HealthCheck'],
      'ParserService': ['Parse', 'HealthCheck'],
      'ChunkerService': ['Chunk', 'HealthCheck'],
      'EmbedderService': ['Embed', 'HealthCheck'],
      'ConnectorEngine': ['Connect', 'Disconnect', 'GetStatus'],
      'ModuleRegistrationService': ['RegisterModule', 'DeregisterModule', 'ListModules'],
      'PipeStreamEngine': ['CreatePipeline', 'DeletePipeline', 'ExecutePipeline'],
      // Consul HTTP API endpoints
      'Agent API': ['/agent/self', '/agent/members', '/agent/services', '/agent/checks'],
      'Catalog API': ['/catalog/nodes', '/catalog/services', '/catalog/datacenters'],
      'Health API': ['/health/node/:node', '/health/service/:service', '/health/state/:state'],
      'KV Store API': ['/kv/:key', '/kv/:key?recurse', '/kv/:key?keys'],
      'ACL API': ['/acl/tokens', '/acl/policies', '/acl/roles']
    };
    
    return knownMethods[serviceName] || [];
  }
  
  getConsulRole(instance) {
    // Check if this is a server or client agent based on port or metadata
    if (instance.port === 8300 || instance.port === 38500) {
      return 'Server (Leader)';
    } else if (instance.port === 8501) {
      return 'Client Agent';
    }
    // Check metadata for role
    if (instance.metadata?.role) {
      return instance.metadata.role;
    }
    // Default based on common patterns
    return instance.address === '127.0.0.1' ? 'Local Agent' : 'Remote Agent';
  }

  render() {
    const { service, instance, instanceCount = 1, allInstances = [] } = this;
    if (!service || !instance) return html``;

    const isHealthy = instance.healthy;
    const isDuplicate = service.hasDuplicates;
    const hasStack = instanceCount > 1;
    const isModule = service.type === 'module';
    const isRegistered = instance.registered;
    
    // For modules with multiple instances, collect all unique gRPC services
    const allGrpcServices = new Set();
    if (isModule && allInstances.length > 0) {
      allInstances.forEach(inst => {
        // Try both camelCase and snake_case
        const services = inst.grpcServices || inst.grpc_services;
        if (services) {
          services.forEach(svc => allGrpcServices.add(svc));
        }
      });
    } else {
      // Try both camelCase and snake_case
      const services = instance.grpcServices || instance.grpc_services;
      if (services) {
        services.forEach(svc => allGrpcServices.add(svc));
      }
    }
    const grpcServicesList = Array.from(allGrpcServices).sort();

    return html`
      <div class="stack-container">
        ${hasStack ? html`
          <div class="stack-shadow ${instanceCount > 2 ? 'double' : ''}"></div>
        ` : ''}
        
        <div class="service-box ${isHealthy ? 'healthy' : 'unhealthy'}" 
             @click=${this.toggleExpanded}>
          
          <!-- Module action buttons on the right -->
          <div class="module-top-actions">
            <!-- Instance count button -->
            <button 
              class="top-action"
              style="background: white; color: #1976d2; font-weight: normal; font-size: 12px; min-width: 24px;"
              @click=${(e) => e.stopPropagation()}
              title="Instance count">
              ${instanceCount}
              <div class="instance-tooltip">
                ${(allInstances.length > 0 ? allInstances : [instance]).map(inst => html`
                  <div class="tooltip-instance">
                    ${inst.moduleId || inst.module_id || inst.id || inst.address}:${inst.port}
                  </div>
                `)}
              </div>
            </button>
            
            ${isModule && isRegistered ? html`
              <!-- Pause/Enable button -->
              ${isHealthy ? html`
                <button 
                  class="top-action pause"
                  @click=${(e) => {
                    e.stopPropagation();
                    this.handleModuleAction('disable-all', allInstances.length > 0 ? allInstances : [instance]);
                  }}
                  title="Disable all instances">
                  <svg fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/>
                  </svg>
                </button>
              ` : html`
                <button 
                  class="top-action pause"
                  @click=${(e) => {
                    e.stopPropagation();
                    this.handleModuleAction('enable-all', allInstances.length > 0 ? allInstances : [instance]);
                  }}
                  title="Enable all instances">
                  <svg fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clip-rule="evenodd"/>
                  </svg>
                </button>
              `}
              <!-- Deregister button -->
              <button 
                class="top-action danger"
                @click=${(e) => {
                  e.stopPropagation();
                  if (confirm(`Are you sure you want to deregister all instances of ${service.name}?`)) {
                    this.handleModuleAction('deregister-all', allInstances.length > 0 ? allInstances : [instance]);
                  }
                }}
                title="Deregister module">
                <svg fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                </svg>
              </button>
            ` : ''}
          </div>
        
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


        <div class="metadata ${this.expanded ? 'show' : ''}">
          ${grpcServicesList.length > 0 ? html`
            <div class="grpc-services" style="margin-bottom: 12px;">
              <strong style="color: #666; font-size: 12px;">
                ${service.name === 'consul' ? 'HTTP APIs' : 'gRPC Services'} (${grpcServicesList.length}):
              </strong>
              ${grpcServicesList.map(svc => html`
                <div class="grpc-service" style="font-family: monospace; font-size: 11px; color: #1976d2; cursor: help;" title="Hover to see methods">
                  📡 ${svc}
                  ${this.getServiceMethods(svc).length > 0 ? html`
                    <div class="grpc-service-methods">
                      ${this.getServiceMethods(svc).map(method => html`
                        <div>• ${method}${service.name === 'consul' ? '' : '()'}</div>
                      `)}
                    </div>
                  ` : ''}
                </div>
              `)}
            </div>
            <hr style="margin: 8px 0; border: none; border-top: 1px solid #eee;">
          ` : ''}
          ${instance.sidecars && instance.sidecars.length > 0 ? html`
            <div class="sidecars-section" style="margin-bottom: 12px;">
              <strong style="color: #666; font-size: 12px;">
                Sidecars (${instance.sidecars.length}):
              </strong>
              ${instance.sidecars.map(sidecar => html`
                <div class="sidecar-info" style="margin: 4px 0 4px 8px; font-size: 11px;">
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <img src="/sidecar.svg" alt="Sidecar icon" style="width: 14px; height: 14px;">
                    <span style="font-weight: 500; color: ${sidecar.healthy ? '#4caf50' : '#f44336'};">
                      ${sidecar.name}
                    </span>
                    <span style="color: #666;">(${sidecar.type})</span>
                  </div>
                  <div style="margin-left: 20px; color: #999;">
                    ${sidecar.address}:${sidecar.port} - ${sidecar.healthy ? 'Healthy' : 'Unhealthy'}
                  </div>
                </div>
              `)}
            </div>
            <hr style="margin: 8px 0; border: none; border-top: 1px solid #eee;">
          ` : ''}
          ${hasStack ? html`
            <div class="metadata-item">
              <span class="metadata-key">Instance IDs:</span>
              <span>${allInstances.map(inst => inst.moduleId || inst.module_id || inst.id || 'N/A').join(', ')}</span>
            </div>
            <hr style="margin: 8px 0; border: none; border-top: 1px solid #eee;">
          ` : ''}
          ${service.name === 'consul' ? html`
            <div class="metadata-item">
              <span class="metadata-key">Role:</span>
              <span>${this.getConsulRole(instance)}</span>
            </div>
            <div class="metadata-item">
              <span class="metadata-key">Version:</span>
              <span>${instance.version || 'Unknown'}</span>
            </div>
            <div class="metadata-item">
              <span class="metadata-key">Datacenter:</span>
              <span>${instance.metadata?.dc || 'dc1'}</span>
            </div>
            <div class="metadata-item">
              <span class="metadata-key">Node:</span>
              <span>${instance.address}</span>
            </div>
            ${hasStack ? html`
              <div class="metadata-item">
                <span class="metadata-key">Total Agents:</span>
                <span>${instanceCount}</span>
              </div>
            ` : ''}
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
          ${hasStack && allInstances ? html`
            <!-- Multiple instances - show each one with its own controls -->
            <div class="instances-list">
              <h4 style="margin: 16px 0 8px 0; font-size: 14px; color: #666;">Module Instances:</h4>
              ${allInstances.map((inst, index) => html`
                <div class="instance-item" style="border: 1px solid #e0e0e0; border-radius: 8px; padding: 12px; margin-bottom: 8px;">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <div style="font-weight: 500;">Instance ${index + 1}</div>
                    <div style="font-size: 12px; color: #666;">${inst.address}:${inst.port}</div>
                  </div>
                  ${(() => {
                    const services = inst.grpcServices || inst.grpc_services;
                    return services && services.length > 0 ? html`
                      <div style="margin-bottom: 8px; padding: 6px; background: #f8f9fa; border-radius: 4px;">
                        <div style="font-size: 11px; color: #666; font-weight: 500; margin-bottom: 4px;">gRPC Services:</div>
                        ${services.map(svc => html`
                          <div style="font-family: monospace; font-size: 10px; color: #1976d2; margin-left: 8px;">
                            • ${svc}
                          </div>
                        `)}
                      </div>
                    ` : '';
                  })()}
                  <div class="module-actions" style="gap: 8px;">
                    ${inst.registered ? html`
                      ${inst.healthy ? html`
                        <button 
                          class="module-action"
                          @click=${(e) => { e.stopPropagation(); this.handleModuleAction('disable', inst); }}>
                          Disable
                        </button>
                      ` : html`
                        <button 
                          class="module-action primary"
                          @click=${(e) => { e.stopPropagation(); this.handleModuleAction('enable', inst); }}>
                          Enable
                        </button>
                      `}
                      <button 
                        class="module-action danger"
                        @click=${(e) => { e.stopPropagation(); this.handleModuleAction('deregister', inst); }}>
                        Deregister
                      </button>
                      ${this.isDevMode ? html`
                        <button 
                          class="module-action danger"
                          style="border: 2px dashed #ff9800;"
                          @click=${(e) => { e.stopPropagation(); this.handleModuleAction('undeploy', inst); }}
                          title="Remove this instance (Dev Only)">
                          Remove
                        </button>
                      ` : ''}
                    ` : html`
                      ${inst.healthy ? html`
                        <button 
                          class="module-action primary"
                          @click=${(e) => { e.stopPropagation(); this.handleModuleAction('register', inst); }}>
                          Register
                        </button>
                      ` : html`
                        <button class="module-action" disabled>
                          Unhealthy
                        </button>
                      `}
                    `}
                  </div>
                </div>
              `)}
              ${this.isDevMode ? html`
                <button 
                  class="module-action"
                  style="width: 100%; margin-top: 8px; background: #ff9800; color: white; border-color: #ff9800;"
                  @click=${(e) => {
                    e.stopPropagation();
                    this.handleModuleAction('scale-up', instance);
                  }}
                  title="Deploy additional instance (Dev Only)">
                  <svg style="width: 16px; height: 16px; margin-right: 4px;" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
                  </svg>
                  Deploy Additional Instance
                </button>
              ` : ''}
            </div>
          ` : html`
            <!-- Single instance - show normal actions -->
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
                ${this.isDevMode ? html`
                  <button 
                    class="module-action danger"
                    style="border: 2px dashed #ff9800;"
                    @click=${() => this.handleModuleAction('undeploy', instance)}
                    title="Undeploy module (Dev Only)">
                    Undeploy
                  </button>
                  <button 
                    class="module-action primary"
                    style="border: 2px dashed #ff9800;"
                    @click=${(e) => {
                      e.stopPropagation();
                      this.handleModuleAction('scale-up', instance);
                    }}
                    title="Deploy additional instance (Dev Only)">
                    Deploy Additional
                  </button>
                ` : ''}
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
          `}
        ` : ''}
        </div>
      </div>
    `;
  }
}

customElements.define('service-box', ServiceBox);