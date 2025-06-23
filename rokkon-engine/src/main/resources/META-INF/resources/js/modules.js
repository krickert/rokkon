/**
 * Modules Module - Handles module registration and management
 */
const Modules = {
    // Show register module modal
    showRegisterModuleModal() {
        const modal = new bootstrap.Modal(document.getElementById('registerModuleModal'));
        modal.show();
    },

    // Register a new module
    async registerModule() {
        const data = {
            module_name: document.getElementById('moduleName').value,
            implementation_id: document.getElementById('implementationId').value,
            host: document.getElementById('moduleHost').value,
            port: parseInt(document.getElementById('modulePort').value),
            service_type: document.getElementById('serviceType').value,
            version: "1.0.0"
        };
        
        try {
            const result = await API.registerModule(data);
            if (result.success) {
                bootstrap.Modal.getInstance(document.getElementById('registerModuleModal')).hide();
                await Dashboard.loadDashboard();
                Dashboard.showSuccess('Module registered successfully');
            } else {
                Dashboard.showError('Failed to register module: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to register module: ' + error.message);
        }
    },

    // Register module from Consul discovery
    async registerModuleFromConsul(moduleName, address, port) {
        if (!confirm(`Register module "${moduleName}" at ${address}:${port}?`)) return;
        
        const data = {
            module_name: moduleName,
            implementation_id: moduleName,
            host: address,
            port: port,
            service_type: 'GRPC',
            version: "1.0.0"
        };
        
        try {
            const result = await API.registerModule(data);
            if (result.success) {
                await Dashboard.loadDashboard();
                Dashboard.showSuccess('Module registered successfully');
            } else {
                Dashboard.showError('Failed to register module: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to register module: ' + error.message);
        }
    },

    // Enable a module
    async enableModule(moduleId) {
        try {
            const result = await API.enableModule(moduleId);
            if (result.success) {
                await Dashboard.loadDashboard();
                Dashboard.showSuccess('Module enabled successfully');
            } else {
                Dashboard.showError('Failed to enable module: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to enable module: ' + error.message);
        }
    },

    // Disable a module
    async disableModule(moduleId) {
        try {
            const result = await API.disableModule(moduleId);
            if (result.success) {
                await Dashboard.loadDashboard();
                Dashboard.showSuccess('Module disabled successfully');
            } else {
                Dashboard.showError('Failed to disable module: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to disable module: ' + error.message);
        }
    },

    // Deregister a module
    async deregisterModule(moduleId) {
        if (!confirm('Are you sure you want to deregister this module?')) return;
        
        try {
            const result = await API.deregisterModule(moduleId);
            if (result.success) {
                await Dashboard.loadDashboard();
                Dashboard.showSuccess('Module deregistered successfully');
            } else {
                Dashboard.showError('Failed to deregister module: ' + result.message);
            }
        } catch (error) {
            Dashboard.showError('Failed to deregister module: ' + error.message);
        }
    },

    // Cleanup zombie instances
    async cleanupZombies() {
        if (!confirm('Clean up all zombie instances?')) return;
        
        try {
            const result = await API.cleanupZombies();
            Dashboard.showSuccess(`Cleanup complete. Removed ${result.cleaned_count || 0} zombie instances.`);
            await Dashboard.loadDashboard();
        } catch (error) {
            Dashboard.showError('Failed to cleanup zombies: ' + error.message);
        }
    },

    // Load registered modules for dropdowns
    async loadRegisteredModules() {
        try {
            const modules = await API.listModules();
            return modules.filter(m => m.enabled);
        } catch (error) {
            console.error('Failed to load registered modules:', error);
            return [];
        }
    },

    // Create module dropdown options
    createModuleOptions(modules, selectedModule = '') {
        return modules.map(module => 
            `<option value="${module.module_name}" ${module.module_name === selectedModule ? 'selected' : ''}>
                ${module.module_name} (${module.version})
            </option>`
        ).join('');
    }
};