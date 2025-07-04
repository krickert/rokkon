import { LitElement, html, css } from 'lit';

export class NavigationHeader extends LitElement {
  static properties = {
    engineInfo: { type: Object },
    autoRefresh: { type: Boolean },
    refreshInterval: { type: Number },
    showInfoPopup: { type: Boolean }
  };

  static styles = css`
    :host {
      display: block;
      background: white;
      border-bottom: 1px solid #e0e0e0;
      box-shadow: 0 1px 4px rgba(0,0,0,0.05);
    }

    .header-container {
      max-width: 1400px;
      margin: 0 auto;
      padding: 16px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .title-section {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .app-title {
      font-size: 24px;
      font-weight: 600;
      color: #333;
      margin: 0;
    }

    .status-badge {
      padding: 6px 12px;
      border-radius: 16px;
      font-size: 12px;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .status-badge.online {
      background: #e8f5e9;
      color: #2e7d32;
    }

    .status-badge.offline {
      background: #ffebee;
      color: #c62828;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      display: inline-block;
    }

    .status-dot.online {
      background: #4caf50;
      animation: pulse 2s infinite;
    }

    .status-dot.offline {
      background: #f44336;
    }

    @keyframes pulse {
      0% {
        box-shadow: 0 0 0 0 rgba(76, 175, 80, 0.7);
      }
      70% {
        box-shadow: 0 0 0 10px rgba(76, 175, 80, 0);
      }
      100% {
        box-shadow: 0 0 0 0 rgba(76, 175, 80, 0);
      }
    }

    .controls-section {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .refresh-controls {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 16px;
      background: #f5f5f5;
      border-radius: 8px;
    }

    .refresh-label {
      font-size: 14px;
      color: #666;
    }

    .toggle-switch {
      position: relative;
      width: 48px;
      height: 24px;
      cursor: pointer;
    }

    .toggle-switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .toggle-slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: #ccc;
      transition: .4s;
      border-radius: 24px;
    }

    .toggle-slider:before {
      position: absolute;
      content: "";
      height: 16px;
      width: 16px;
      left: 4px;
      bottom: 4px;
      background-color: white;
      transition: .4s;
      border-radius: 50%;
    }

    input:checked + .toggle-slider {
      background-color: #1976d2;
    }

    input:checked + .toggle-slider:before {
      transform: translateX(24px);
    }

    .interval-select {
      padding: 4px 8px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
      background: white;
    }

    .version-info {
      font-size: 12px;
      color: #999;
      margin-left: 8px;
    }

    .info-tooltip {
      position: relative;
      display: inline-block;
      cursor: pointer;
    }

    .info-icon {
      width: 16px;
      height: 16px;
      margin-left: 4px;
      color: #999;
      vertical-align: middle;
    }

    .info-icon:hover {
      color: #1976d2;
    }

    .info-popup {
      position: absolute;
      top: 100%;
      left: 50%;
      transform: translateX(-50%);
      margin-top: 8px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      padding: 16px;
      min-width: 350px;
      z-index: 1000;
      display: none;
    }

    .info-popup.show {
      display: block;
    }

    .info-section {
      margin-bottom: 12px;
    }

    .info-section:last-child {
      margin-bottom: 0;
    }

    .info-section-title {
      font-size: 12px;
      font-weight: 600;
      color: #666;
      margin-bottom: 4px;
      text-transform: uppercase;
    }

    .info-row {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      padding: 2px 0;
    }

    .info-label {
      color: #666;
    }

    .info-value {
      color: #333;
      font-family: monospace;
    }
  `;

  toggleRefresh() {
    this.dispatchEvent(new CustomEvent('toggle-refresh', { bubbles: true }));
  }

  constructor() {
    super();
    this.showInfoPopup = false;
  }

  connectedCallback() {
    super.connectedCallback();
    this.boundClosePopup = this.closePopup.bind(this);
    document.addEventListener('click', this.boundClosePopup);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('click', this.boundClosePopup);
  }

  changeInterval(e) {
    const interval = parseInt(e.target.value);
    this.dispatchEvent(new CustomEvent('change-interval', { 
      detail: { interval },
      bubbles: true 
    }));
  }

  toggleInfoPopup(e) {
    e.stopPropagation();
    this.showInfoPopup = !this.showInfoPopup;
  }

  closePopup() {
    this.showInfoPopup = false;
  }

  formatMemory(bytes) {
    const mb = bytes / (1024 * 1024);
    return mb.toFixed(1) + ' MB';
  }

  formatUptime(ms) {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);
    
    if (days > 0) return `${days}d ${hours % 24}h`;
    if (hours > 0) return `${hours}h ${minutes % 60}m`;
    if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
    return `${seconds}s`;
  }

  formatBuildTime(timeStr) {
    if (!timeStr) return 'Unknown';
    try {
      const date = new Date(timeStr);
      return date.toLocaleString();
    } catch (e) {
      return timeStr;
    }
  }

  render() {
    const { engineInfo, autoRefresh, refreshInterval } = this;
    const isOnline = engineInfo?.status === 'online';
    const appName = engineInfo?.name || 'Pipeline Engine';
    const version = engineInfo?.version || engineInfo?.build?.version || '';

    return html`
      <div class="header-container">
        <div class="title-section">
          <h1 class="app-title">${appName}</h1>
          <div class="status-badge ${isOnline ? 'online' : 'offline'}">
            <span class="status-dot ${isOnline ? 'online' : 'offline'}"></span>
            ${isOnline ? 'Online' : 'Offline'}
          </div>
          ${version ? html`
            <span class="version-info">v${version}</span>
            <span class="info-tooltip" @click=${(e) => this.toggleInfoPopup(e)}>
              <svg class="info-icon" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/>
              </svg>
              ${engineInfo ? html`
                <div class="info-popup ${this.showInfoPopup ? 'show' : ''}" @click=${(e) => e.stopPropagation()}>
                  <div class="info-section">
                    <div class="info-section-title">Build Info</div>
                    <div class="info-row">
                      <span class="info-label">Version:</span>
                      <span class="info-value">${engineInfo.build?.version || engineInfo.version || 'dev'}</span>
                    </div>
                    ${engineInfo.build?.time ? html`
                      <div class="info-row">
                        <span class="info-label">Build Time:</span>
                        <span class="info-value">${this.formatBuildTime(engineInfo.build.time)}</span>
                      </div>
                    ` : ''}
                    <div class="info-row">
                      <span class="info-label">Profile:</span>
                      <span class="info-value">${engineInfo.profile}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">Cluster:</span>
                      <span class="info-value">${engineInfo.clusterName}</span>
                    </div>
                  </div>
                  
                  ${engineInfo.java ? html`
                    <div class="info-section">
                      <div class="info-section-title">Java Runtime</div>
                      <div class="info-row">
                        <span class="info-label">Version:</span>
                        <span class="info-value">${engineInfo.java.version}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">Vendor:</span>
                        <span class="info-value">${engineInfo.java.vendor}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">VM:</span>
                        <span class="info-value">${engineInfo.java['vm.name']}</span>
                      </div>
                    </div>
                  ` : ''}
                  
                  ${engineInfo.runtime ? html`
                    <div class="info-section">
                      <div class="info-section-title">Runtime Stats</div>
                      <div class="info-row">
                        <span class="info-label">Uptime:</span>
                        <span class="info-value">${this.formatUptime(engineInfo.runtime.uptime)}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">Memory (Used/Total):</span>
                        <span class="info-value">
                          ${this.formatMemory(engineInfo.runtime.totalMemory - engineInfo.runtime.freeMemory)} / 
                          ${this.formatMemory(engineInfo.runtime.totalMemory)}
                        </span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">Max Memory:</span>
                        <span class="info-value">${this.formatMemory(engineInfo.runtime.maxMemory)}</span>
                      </div>
                    </div>
                  ` : ''}
                  
                  ${engineInfo.os ? html`
                    <div class="info-section">
                      <div class="info-section-title">System Info</div>
                      <div class="info-row">
                        <span class="info-label">OS:</span>
                        <span class="info-value">${engineInfo.os.name} ${engineInfo.os.version}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">Architecture:</span>
                        <span class="info-value">${engineInfo.os.arch}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-label">CPUs:</span>
                        <span class="info-value">${engineInfo.os.availableProcessors}</span>
                      </div>
                    </div>
                  ` : ''}
                </div>
              ` : ''}
            </span>
          ` : ''}
        </div>

        <div class="controls-section">
          <div class="refresh-controls">
            <span class="refresh-label">Auto Refresh</span>
            <label class="toggle-switch">
              <input 
                type="checkbox" 
                .checked=${autoRefresh}
                @change=${this.toggleRefresh}>
              <span class="toggle-slider"></span>
            </label>
            ${autoRefresh ? html`
              <select class="interval-select" @change=${this.changeInterval}>
                <option value="30000" ?selected=${refreshInterval === 30000}>30s</option>
                <option value="60000" ?selected=${refreshInterval === 60000}>60s</option>
              </select>
            ` : ''}
          </div>
        </div>
      </div>
    `;
  }
}

customElements.define('navigation-header', NavigationHeader);