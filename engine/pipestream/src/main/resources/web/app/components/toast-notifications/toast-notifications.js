import { LitElement, html, css } from 'lit';

export class ToastNotifications extends LitElement {
  static properties = {
    toasts: { type: Array }
  };

  static styles = css`
    :host {
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      z-index: 2000;
      display: flex;
      flex-direction: column;
      gap: 12px;
      pointer-events: none;
    }

    .toast {
      background: white;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      padding: 16px 20px;
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 300px;
      max-width: 500px;
      pointer-events: auto;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        transform: translateY(-20px);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }

    .toast.removing {
      animation: slideOut 0.3s ease-in forwards;
    }

    @keyframes slideOut {
      from {
        transform: translateY(0);
        opacity: 1;
      }
      to {
        transform: translateY(-20px);
        opacity: 0;
      }
    }

    .toast.success {
      border-left: 4px solid #4caf50;
    }

    .toast.error {
      border-left: 4px solid #f44336;
    }

    .toast.info {
      border-left: 4px solid #2196f3;
    }

    .toast.warning {
      border-left: 4px solid #ff9800;
    }

    .toast-icon {
      width: 24px;
      height: 24px;
      flex-shrink: 0;
    }

    .toast-icon.success {
      color: #4caf50;
    }

    .toast-icon.error {
      color: #f44336;
    }

    .toast-icon.info {
      color: #2196f3;
    }

    .toast-icon.warning {
      color: #ff9800;
    }

    .toast-content {
      flex: 1;
      font-size: 14px;
      color: #333;
    }

    .toast-close {
      width: 20px;
      height: 20px;
      padding: 0;
      border: none;
      background: none;
      cursor: pointer;
      color: #999;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .toast-close:hover {
      color: #333;
    }
  `;

  constructor() {
    super();
    this.toasts = [];
  }

  connectedCallback() {
    super.connectedCallback();
    window.addEventListener('show-toast', this.handleShowToast.bind(this));
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('show-toast', this.handleShowToast.bind(this));
  }

  handleShowToast(event) {
    const { message, type = 'info', duration = 5000 } = event.detail;
    const id = Date.now();
    
    this.toasts = [...this.toasts, { id, message, type }];
    
    if (duration > 0) {
      setTimeout(() => this.removeToast(id), duration);
    }
  }

  removeToast(id) {
    const index = this.toasts.findIndex(t => t.id === id);
    if (index !== -1) {
      // Add removing class for animation
      this.toasts = this.toasts.map(t => 
        t.id === id ? { ...t, removing: true } : t
      );
      
      // Actually remove after animation
      setTimeout(() => {
        this.toasts = this.toasts.filter(t => t.id !== id);
      }, 300);
    }
  }

  getIcon(type) {
    switch (type) {
      case 'success':
        return html`<svg class="toast-icon success" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
        </svg>`;
      case 'error':
        return html`<svg class="toast-icon error" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
        </svg>`;
      case 'warning':
        return html`<svg class="toast-icon warning" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
        </svg>`;
      default:
        return html`<svg class="toast-icon info" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/>
        </svg>`;
    }
  }

  render() {
    return html`
      ${this.toasts.map(toast => html`
        <div class="toast ${toast.type} ${toast.removing ? 'removing' : ''}">
          ${this.getIcon(toast.type)}
          <div class="toast-content">${toast.message}</div>
          <button class="toast-close" @click=${() => this.removeToast(toast.id)}>
            <svg fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
            </svg>
          </button>
        </div>
      `)}
    `;
  }
}

customElements.define('toast-notifications', ToastNotifications);