/**
 * Services Module - Handles service display and management
 */
const Services = {
    // Track expanded states
    expandedStates: {
        grpcServices: new Set(),
        moduleInstances: new Set()
    },

    // Update statistics display
    updateStatistics(stats) {
        document.getElementById('totalModules').textContent = stats.total_modules || 0;
        document.getElementById('healthyModules').textContent = stats.healthy_modules || 0;
        document.getElementById('baseServices').textContent = stats.total_base_services || 0;
        document.getElementById('zombieCount').textContent = stats.zombie_count || 0;
    },

    // Render all services
    renderServices(data) {
        const servicesList = document.getElementById('allServicesList');
        let html = '';
        
        // Add zombie warning if present
        if (data.statistics.zombie_count > 0) {
            html += this.renderZombieWarning(data.statistics.zombie_count);
        }
        
        html += `
            <div class="table-responsive">
                <table class="table table-hover">
                    <thead>
                        <tr>
                            <th>Service Name</th>
                            <th>Status</th>
                            <th>Instances</th>
                            <th>Type</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
        `;
        
        // Render base services
        if (data.base_services && data.base_services.length > 0) {
            html += this.renderBaseServices(data.base_services);
        }
        
        // Render module services
        if (data.module_services && data.module_services.length > 0) {
            html += this.renderModuleServices(data.module_services);
        }
        
        html += `
                    </tbody>
                </table>
            </div>
        `;
        
        servicesList.innerHTML = html;
        
        // Restore expanded states
        this.restoreExpandedStates();
    },

    renderZombieWarning(zombieCount) {
        return `
            <div class="alert alert-warning d-flex align-items-center mb-3" role="alert">
                <i class="bi bi-exclamation-triangle-fill me-2"></i>
                <div>
                    <strong>Zombie Instances Detected</strong><br>
                    <small>Found ${zombieCount} instance(s) in Consul that are not registered in Rokkon.</small>
                </div>
            </div>
        `;
    },

    renderBaseServices(baseServices) {
        let html = `
            <tr class="table-light">
                <td colspan="5" class="fw-bold bg-light">
                    <i class="bi bi-server me-2"></i>Base Services
                </td>
            </tr>
        `;
        
        baseServices.forEach(service => {
            const healthyCount = service.instances.filter(i => i.healthy).length;
            const totalCount = service.instances.length;
            const hasGrpcServices = service.instances.some(i => i.grpc_services && i.grpc_services.length > 0);
            
            html += `
                <tr class="service-row ${hasGrpcServices ? 'has-grpc' : ''}" onclick="Services.toggleGrpcServices('${service.name}')">
                    <td>
                        ${hasGrpcServices ? `<i class="bi bi-chevron-right me-2" id="chevron-${service.name}"></i>` : ''}
                        <strong>${service.name}</strong>
                        <span class="badge bg-secondary ms-2">${service.type}</span>
                    </td>
                    <td>
                        <span class="badge ${healthyCount === totalCount ? 'bg-success' : healthyCount > 0 ? 'bg-warning' : 'bg-danger'}">
                            ${healthyCount}/${totalCount} Healthy
                        </span>
                    </td>
                    <td>${totalCount}</td>
                    <td>Infrastructure</td>
                    <td>-</td>
                </tr>
            `;
            
            // Add gRPC services row if available
            if (hasGrpcServices) {
                const grpcServices = [...new Set(service.instances.flatMap(i => i.grpc_services || []))];
                html += `
                    <tr class="grpc-services" id="grpc-${service.name}">
                        <td colspan="5" class="ps-5">
                            <strong>gRPC Services:</strong>
                            ${grpcServices.map(s => `<span class="badge bg-info ms-2">${s}</span>`).join('')}
                        </td>
                    </tr>
                `;
            }
        });
        
        return html;
    },

    renderModuleServices(moduleServices) {
        let html = `
            <tr class="table-light">
                <td colspan="5" class="fw-bold bg-light">
                    <i class="bi bi-puzzle me-2"></i>Module Services
                </td>
            </tr>
        `;
        
        moduleServices.forEach(module => {
            const healthyCount = module.instances.filter(i => i.healthy && i.registered).length;
            const totalCount = module.instances.length;
            const registeredCount = module.instances.filter(i => i.registered).length;
            
            // Module group header
            html += `
                <tr class="table-primary">
                    <td colspan="5">
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <strong>${module.service_name}</strong>
                                <span class="badge bg-info ms-2">Module Group</span>
                                <span class="badge ${healthyCount === registeredCount ? 'bg-success' : healthyCount > 0 ? 'bg-warning' : 'bg-danger'} ms-2">
                                    ${healthyCount}/${registeredCount} Healthy
                                </span>
                                ${registeredCount < totalCount ? `<span class="badge bg-warning ms-2">${totalCount - registeredCount} Unregistered</span>` : ''}
                            </div>
                            <button class="btn btn-sm btn-outline-secondary" onclick="Services.toggleModuleInstances('${module.module_name}')">
                                <i class="bi bi-chevron-down" id="chevron-${module.module_name}"></i> Show Instances
                            </button>
                        </div>
                    </td>
                </tr>
            `;
            
            // Module instances
            html += this.renderModuleInstances(module);
        });
        
        return html;
    },

    renderModuleInstances(module) {
        let html = '';
        
        module.instances.forEach((instance, index) => {
            const statusBadge = instance.healthy ? 
                '<span class="badge bg-success">Healthy</span>' : 
                '<span class="badge bg-danger">Unhealthy</span>';
            
            let actionButtons = '';
            if (instance.registered) {
                if (instance.enabled) {
                    actionButtons = `
                        <button class="btn btn-sm btn-warning" onclick="Modules.disableModule('${instance.module_id}')">
                            <i class="bi bi-pause-circle"></i> Disable
                        </button>
                        <button class="btn btn-sm btn-danger ms-1" onclick="Modules.deregisterModule('${instance.module_id}')">
                            <i class="bi bi-x-circle"></i> Deregister
                        </button>
                    `;
                } else {
                    actionButtons = `
                        <button class="btn btn-sm btn-success" onclick="Modules.enableModule('${instance.module_id}')">
                            <i class="bi bi-play-circle"></i> Enable
                        </button>
                        <button class="btn btn-sm btn-danger ms-1" onclick="Modules.deregisterModule('${instance.module_id}')">
                            <i class="bi bi-x-circle"></i> Deregister
                        </button>
                    `;
                }
            } else {
                actionButtons = `
                    <button class="btn btn-sm btn-outline-primary" 
                            onclick="Modules.registerModuleFromConsul('${module.module_name}', '${instance.address}', ${instance.port})">
                        <i class="bi bi-plus-circle"></i> Register
                    </button>
                `;
            }
            
            const rowClass = instance.enabled === false ? 'table-secondary opacity-75' : '';
            const nameStyle = instance.enabled === false ? 'text-decoration: line-through;' : '';
            
            // Container info
            let containerInfo = '';
            if (instance.container_id || instance.container_name) {
                const containerId = instance.container_id ? instance.container_id.substring(0, 12) : '';
                const containerName = instance.container_name || '';
                containerInfo = `
                    <br><small class="text-muted">
                        ${containerName ? `<i class="bi bi-box-seam"></i> ${containerName}` : ''}
                        ${containerId ? `(${containerId})` : ''}
                    </small>
                `;
            }
            
            html += `
                <tr class="${rowClass} module-instance-${module.module_name}" style="display: none;">
                    <td class="ps-4">
                        <i class="bi bi-arrow-return-right me-2"></i>
                        <span style="${nameStyle}">Instance ${index + 1}</span>
                        ${!instance.enabled ? '<span class="badge bg-secondary ms-2">Disabled</span>' : ''}
                        ${!instance.registered ? '<span class="badge bg-warning ms-2">Unregistered</span>' : ''}
                        ${containerInfo}
                    </td>
                    <td>${statusBadge}</td>
                    <td>${instance.address}:${instance.port}</td>
                    <td>Processor</td>
                    <td>${actionButtons}</td>
                </tr>
            `;
        });
        
        return html;
    },

    // Toggle gRPC services display
    toggleGrpcServices(serviceName) {
        const grpcRow = document.getElementById(`grpc-${serviceName}`);
        const chevron = document.getElementById(`chevron-${serviceName}`);
        
        if (grpcRow) {
            const isExpanded = grpcRow.classList.contains('show');
            if (isExpanded) {
                grpcRow.classList.remove('show');
                this.expandedStates.grpcServices.delete(serviceName);
            } else {
                grpcRow.classList.add('show');
                this.expandedStates.grpcServices.add(serviceName);
            }
        }
        
        if (chevron) {
            chevron.classList.toggle('bi-chevron-right');
            chevron.classList.toggle('bi-chevron-down');
        }
    },

    // Toggle module instances display
    toggleModuleInstances(moduleName) {
        const instances = document.querySelectorAll(`.module-instance-${moduleName}`);
        const chevron = document.getElementById(`chevron-${moduleName}`);
        
        const isExpanded = instances.length > 0 && instances[0].style.display !== 'none';
        
        instances.forEach(row => {
            row.style.display = isExpanded ? 'none' : 'table-row';
        });
        
        if (isExpanded) {
            this.expandedStates.moduleInstances.delete(moduleName);
        } else {
            this.expandedStates.moduleInstances.add(moduleName);
        }
        
        if (chevron) {
            chevron.classList.toggle('bi-chevron-down');
            chevron.classList.toggle('bi-chevron-right');
        }
    },

    // Restore expanded states after rendering
    restoreExpandedStates() {
        // Restore gRPC services
        this.expandedStates.grpcServices.forEach(serviceName => {
            const grpcRow = document.getElementById(`grpc-${serviceName}`);
            const chevron = document.getElementById(`chevron-${serviceName}`);
            
            if (grpcRow) {
                grpcRow.classList.add('show');
            }
            if (chevron) {
                chevron.classList.remove('bi-chevron-right');
                chevron.classList.add('bi-chevron-down');
            }
        });
        
        // Restore module instances
        this.expandedStates.moduleInstances.forEach(moduleName => {
            const instances = document.querySelectorAll(`.module-instance-${moduleName}`);
            const chevron = document.getElementById(`chevron-${moduleName}`);
            
            instances.forEach(row => {
                row.style.display = 'table-row';
            });
            
            if (chevron) {
                chevron.classList.remove('bi-chevron-down');
                chevron.classList.add('bi-chevron-right');
            }
        });
    }
};