/**
 * API Module - Handles all API communications
 */
const API = {
    // Base API endpoints
    endpoints: {
        ping: '/ping',
        consulStatus: '/api/consul/status',
        dashboard: '/api/v1/module-discovery/dashboard',
        modules: '/api/v1/modules',
        allServices: '/api/v1/module-discovery/all-services',
        cleanupZombies: '/api/v1/modules/cleanup-zombies',
        pipelines: '/api/v1/pipelines/definitions',
        clusters: '/api/v1/clusters'
    },

    // Generic fetch wrapper with error handling
    async fetchJson(url, options = {}) {
        try {
            const response = await fetch(url, {
                ...options,
                headers: {
                    'Content-Type': 'application/json',
                    ...options.headers
                }
            });

            if (!response.ok) {
                // For 404s, check if it's an empty collection
                if (response.status === 404 && url.includes('/pipelines/definitions') && options.method === 'GET') {
                    return []; // Return empty array for pipelines
                }

                // Try to get error details from response body
                let errorMessage = response.statusText || `HTTP ${response.status}`;
                try {
                    const contentType = response.headers.get('content-type');
                    if (contentType && contentType.includes('application/json')) {
                        const error = await response.json();
                        errorMessage = error.message || errorMessage;
                    }
                } catch (e) {
                    // If JSON parsing fails, use the default error message
                }

                throw new Error(errorMessage);
            }

            // Handle 204 No Content (typical for DELETE operations)
            if (response.status === 204) {
                return { success: true };
            }

            // Handle empty response body
            const text = await response.text();
            if (!text) {
                return { success: true };
            }

            // Parse JSON response
            try {
                return JSON.parse(text);
            } catch (e) {
                console.warn('Response is not JSON:', text);
                return { success: true, message: text };
            }
        } catch (error) {
            console.error(`API Error (${url}):`, error);
            throw error;
        }
    },

    // Engine status check
    async checkPing() {
        try {
            const response = await fetch(this.endpoints.ping);
            const text = await response.text();
            return { success: true, data: text };
        } catch (error) {
            return { success: false, error: error.message };
        }
    },

    // Consul status check
    async checkConsulStatus() {
        return this.fetchJson(this.endpoints.consulStatus);
    },

    // Dashboard data
    async getDashboard() {
        return this.fetchJson(this.endpoints.dashboard);
    },

    // Module operations
    async registerModule(moduleData) {
        return this.fetchJson(this.endpoints.modules, {
            method: 'POST',
            body: JSON.stringify(moduleData)
        });
    },

    async enableModule(moduleId) {
        return this.fetchJson(`${this.endpoints.modules}/${moduleId}/enable`, {
            method: 'PUT'
        });
    },

    async disableModule(moduleId) {
        return this.fetchJson(`${this.endpoints.modules}/${moduleId}/disable`, {
            method: 'PUT'
        });
    },

    async deregisterModule(moduleId) {
        return this.fetchJson(`${this.endpoints.modules}/${moduleId}`, {
            method: 'DELETE'
        });
    },

    async listModules() {
        return this.fetchJson(this.endpoints.modules);
    },

    async cleanupZombies() {
        return this.fetchJson(this.endpoints.cleanupZombies, {
            method: 'POST'
        });
    },

    // Pipeline operations
    async listPipelines() {
        return this.fetchJson(this.endpoints.pipelines);
    },

    async getPipeline(name) {
        return this.fetchJson(`${this.endpoints.pipelines}/${name}`);
    },

    async createPipeline(pipelineData) {
        return this.fetchJson(this.endpoints.pipelines, {
            method: 'POST',
            body: JSON.stringify(pipelineData)
        });
    },

    async updatePipeline(name, pipelineData) {
        return this.fetchJson(`${this.endpoints.pipelines}/${name}`, {
            method: 'PUT',
            body: JSON.stringify(pipelineData)
        });
    },

    async deletePipeline(name) {
        return this.fetchJson(`${this.endpoints.pipelines}/${name}`, {
            method: 'DELETE'
        });
    },

    async validatePipeline(pipelineData) {
        return this.fetchJson(`${this.endpoints.pipelines}/validate`, {
            method: 'POST',
            body: JSON.stringify(pipelineData)
        });
    },

    // Cluster operations
    async listClusters() {
        return this.fetchJson(this.endpoints.clusters);
    },

    async createCluster(clusterName) {
        const result = await this.fetchJson(`${this.endpoints.clusters}/${clusterName}`, {
            method: 'POST'
        });

        // Transform ValidationResult to expected format
        return {
            success: result.valid === true,
            message: result.errors ? result.errors.join(', ') : ''
        };
    },

    async deleteCluster(clusterName) {
        const result = await this.fetchJson(`${this.endpoints.clusters}/${clusterName}`, {
            method: 'DELETE'
        });

        // Transform ValidationResult to expected format
        return {
            success: result.valid === true,
            message: result.errors ? result.errors.join(', ') : ''
        };
    }
};
