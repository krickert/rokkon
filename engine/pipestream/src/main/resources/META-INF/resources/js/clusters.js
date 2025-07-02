/**
 * Clusters Module - Handles cluster management
 */
const Clusters = {
    // Load and display clusters
    async loadClusters() {
        const clustersList = document.getElementById('clustersList');
        
        try {
            const clusters = await API.listClusters();
            
            if (clusters.length === 0) {
                clustersList.innerHTML = `
                    <div class="alert alert-info">
                        <i class="bi bi-info-circle"></i> No clusters available. Create one to get started.
                    </div>
                `;
                return;
            }
            
            let html = '<div class="list-group">';
            
            clusters.forEach(cluster => {
                const createdAt = new Date(cluster.created_at).toLocaleString();
                const metadata = cluster.metadata?.metadata || {};
                const status = metadata.status || 'unknown';
                const version = metadata.version || 'unknown';
                
                html += `
                    <div class="list-group-item">
                        <div class="d-flex justify-content-between align-items-start">
                            <div>
                                <h6 class="mb-1">${cluster.name}</h6>
                                <p class="mb-1">
                                    <span class="badge bg-${status === 'active' ? 'success' : 'secondary'}">${status}</span>
                                    <small class="text-muted ms-2">Version: ${version}</small>
                                </p>
                                <small class="text-muted">Created: ${createdAt}</small>
                            </div>
                            <div>
                                ${cluster.name !== 'default' ? `
                                    <button class="btn btn-sm btn-outline-danger" onclick="Clusters.deleteCluster('${cluster.name}')">
                                        <i class="bi bi-trash"></i> Delete
                                    </button>
                                ` : '<small class="text-muted">Default cluster cannot be deleted</small>'}
                            </div>
                        </div>
                    </div>
                `;
            });
            
            html += '</div>';
            clustersList.innerHTML = html;
        } catch (error) {
            clustersList.innerHTML = '<p class="text-danger">Failed to load clusters.</p>';
            console.error('Error loading clusters:', error);
        }
    },

    // Show create cluster modal
    showCreateClusterModal() {
        document.getElementById('newClusterName').value = '';
        const modal = new bootstrap.Modal(document.getElementById('createClusterModal'));
        modal.show();
    },

    // Create a new cluster
    async createCluster() {
        const clusterName = document.getElementById('newClusterName').value.trim();
        
        if (!clusterName) {
            Dashboard.showError('Cluster name is required');
            return;
        }
        
        if (!/^[a-z0-9-]+$/.test(clusterName)) {
            Dashboard.showError('Cluster name must contain only lowercase letters, numbers, and hyphens');
            return;
        }
        
        try {
            const result = await API.createCluster(clusterName);
            if (result.success) {
                bootstrap.Modal.getInstance(document.getElementById('createClusterModal')).hide();
                await this.loadClusters();
                Dashboard.showSuccess('Cluster created successfully');
            } else {
                Dashboard.showError('Failed to create cluster: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to create cluster: ' + error.message);
        }
    },

    // Delete a cluster
    async deleteCluster(clusterName) {
        if (clusterName === 'default') {
            Dashboard.showError('Cannot delete the default cluster');
            return;
        }
        
        if (!confirm(`Are you sure you want to delete the cluster "${clusterName}"? This action cannot be undone.`)) {
            return;
        }
        
        try {
            const result = await API.deleteCluster(clusterName);
            if (result.success) {
                await this.loadClusters();
                Dashboard.showSuccess('Cluster deleted successfully');
            } else {
                Dashboard.showError('Failed to delete cluster: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to delete cluster: ' + error.message);
        }
    },

    // Create default cluster if none exist
    async ensureDefaultCluster() {
        try {
            const clusters = await API.listClusters();
            const hasDefault = clusters.some(c => c.name === 'default');
            
            if (!hasDefault && clusters.length === 0) {
                // No clusters at all, create default
                await API.createCluster('default');
                await this.loadClusters();
            }
        } catch (error) {
            console.error('Failed to check/create default cluster:', error);
        }
    }
};