/**
 * Main Dashboard Module - Coordinates all dashboard functionality
 */
const Dashboard = {
    // Current tab
    currentTab: 'services',
    
    // Auto-refresh interval
    refreshInterval: null,
    autoRefreshEnabled: true,
    
    // Initialize dashboard
    async init() {
        console.log('Initializing Rokkon Dashboard...');
        
        // Set up event listeners
        this.setupEventListeners();
        
        // Initial checks
        await this.checkAll();
        
        // Load initial data based on current tab
        await this.loadCurrentTab();
        
        // Start auto-refresh
        this.startAutoRefresh();
        
        // Ensure default cluster exists
        await Clusters.ensureDefaultCluster();
    },

    // Set up event listeners
    setupEventListeners() {
        // Tab switching
        document.querySelectorAll('[data-bs-toggle="tab"]').forEach(tab => {
            tab.addEventListener('shown.bs.tab', (event) => {
                this.currentTab = event.target.getAttribute('data-target');
                this.loadCurrentTab();
            });
        });
        
        // Refresh button
        const refreshBtn = document.getElementById('refreshStatus');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.checkAll());
        }
        
        // Auto-refresh toggle
        const autoRefreshToggle = document.getElementById('autoRefreshToggle');
        if (autoRefreshToggle) {
            autoRefreshToggle.addEventListener('change', (e) => {
                this.autoRefreshEnabled = e.target.checked;
                if (this.autoRefreshEnabled) {
                    this.startAutoRefresh();
                } else {
                    this.stopAutoRefresh();
                }
            });
        }
    },

    // Load data for current tab
    async loadCurrentTab() {
        switch (this.currentTab) {
            case 'services':
                await this.loadDashboard();
                break;
            case 'pipelines':
                await Pipelines.loadPipelines();
                break;
            case 'clusters':
                await Clusters.loadClusters();
                break;
        }
    },

    // Check all system statuses
    async checkAll() {
        await Promise.all([
            this.checkPingStatus(),
            this.checkConsulStatus()
        ]);
    },

    // Check engine ping status
    async checkPingStatus() {
        const result = await API.checkPing();
        const statusEl = document.getElementById('pingStatus');
        const engineStatusEl = document.getElementById('engineStatus');
        
        if (result.success) {
            statusEl.innerHTML = '<span class="text-success">Online</span>';
            engineStatusEl.textContent = 'Online';
            engineStatusEl.className = 'badge bg-success';
        } else {
            statusEl.innerHTML = '<span class="text-danger">Offline</span>';
            engineStatusEl.textContent = 'Offline';
            engineStatusEl.className = 'badge bg-danger';
        }
    },

    // Check Consul status
    async checkConsulStatus() {
        try {
            const data = await API.checkConsulStatus();
            const statusEl = document.getElementById('consulStatus');
            
            if (data.status === 'UP') {
                statusEl.innerHTML = '<span class="text-success">Connected</span>';
            } else {
                statusEl.innerHTML = '<span class="text-danger">Not Connected</span>';
            }
        } catch (error) {
            document.getElementById('consulStatus').innerHTML = '<span class="text-danger">Error</span>';
        }
    },

    // Load dashboard data
    async loadDashboard() {
        try {
            const data = await API.getDashboard();
            Services.updateStatistics(data.statistics);
            Services.renderServices(data);
        } catch (error) {
            console.error('Error loading dashboard:', error);
            document.getElementById('allServicesList').innerHTML = 
                '<p class="text-danger">Failed to load services. Check Consul connection.</p>';
        }
    },

    // Start auto-refresh
    startAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }
        
        this.refreshInterval = setInterval(() => {
            if (this.autoRefreshEnabled) {
                this.loadCurrentTab();
            }
        }, 30000); // Refresh every 30 seconds
    },

    // Stop auto-refresh
    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    },

    // Show success message
    showSuccess(message) {
        this.showToast(message, 'success');
    },

    // Show error message
    showError(message) {
        this.showToast(message, 'danger');
    },

    // Show info message
    showInfo(message) {
        this.showToast(message, 'info');
    },

    // Show toast notification
    showToast(message, type = 'info') {
        const toastContainer = document.getElementById('toastContainer');
        if (!toastContainer) {
            // Create toast container if it doesn't exist
            const container = document.createElement('div');
            container.id = 'toastContainer';
            container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            document.body.appendChild(container);
        }
        
        const toastId = 'toast-' + Date.now();
        const toastHtml = `
            <div id="${toastId}" class="toast align-items-center text-white bg-${type} border-0" role="alert">
                <div class="d-flex">
                    <div class="toast-body">
                        ${message}
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
                </div>
            </div>
        `;
        
        document.getElementById('toastContainer').insertAdjacentHTML('beforeend', toastHtml);
        
        const toastEl = document.getElementById(toastId);
        const toast = new bootstrap.Toast(toastEl, { delay: 5000 });
        toast.show();
        
        // Remove toast element after it's hidden
        toastEl.addEventListener('hidden.bs.toast', () => {
            toastEl.remove();
        });
    }
};

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    Dashboard.init();
});