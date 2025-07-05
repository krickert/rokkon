/**
 * Server-Sent Events service for real-time module deployment updates
 */
export class DeploymentSSE {
  constructor() {
    this.eventSource = null;
    this.listeners = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
    this.isConnecting = false;
  }

  connect() {
    if (this.eventSource && this.eventSource.readyState !== EventSource.CLOSED) {
      return Promise.resolve();
    }

    if (this.isConnecting) {
      return new Promise((resolve) => {
        const checkConnection = setInterval(() => {
          if (this.eventSource && this.eventSource.readyState === EventSource.OPEN) {
            clearInterval(checkConnection);
            resolve();
          }
        }, 100);
      });
    }

    this.isConnecting = true;

    return new Promise((resolve, reject) => {
      try {
        const url = '/api/v1/module-deployment/events';
        this.eventSource = new EventSource(url);

        this.eventSource.onopen = () => {
          console.log('DeploymentSSE connected');
          this.reconnectAttempts = 0;
          this.isConnecting = false;
          resolve();
        };

        this.eventSource.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            this.notifyListeners(data);
          } catch (e) {
            console.error('Failed to parse SSE message:', e);
          }
        };

        this.eventSource.onerror = (error) => {
          console.error('DeploymentSSE error:', error);
          this.isConnecting = false;
          
          // EventSource automatically reconnects, but we can add our own logic
          if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`SSE will auto-reconnect (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
          } else {
            this.disconnect();
            reject(new Error('Max reconnection attempts reached'));
          }
        };

      } catch (error) {
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  disconnect() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    this.listeners.clear();
  }

  /**
   * Add a listener for deployment events
   * @param {string} id - Unique identifier for the listener
   * @param {Function} callback - Function to call with event data
   */
  addListener(id, callback) {
    this.listeners.set(id, callback);
  }

  /**
   * Remove a listener
   * @param {string} id - Listener identifier
   */
  removeListener(id) {
    this.listeners.delete(id);
  }

  /**
   * Notify all listeners of an event
   * @param {Object} data - Event data from SSE
   */
  notifyListeners(data) {
    this.listeners.forEach(callback => {
      try {
        callback(data);
      } catch (e) {
        console.error('Error in SSE listener:', e);
      }
    });
  }
}

// Create singleton instance
export const deploymentSSE = new DeploymentSSE();