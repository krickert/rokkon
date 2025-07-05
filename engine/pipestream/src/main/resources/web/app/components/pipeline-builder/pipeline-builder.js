import { LitElement, html, css } from 'lit';

export class PipelineBuilder extends LitElement {
  static properties = {
    pipeline: { type: Object },
    availableModules: { type: Array },
    selectedNode: { type: Object },
    selectedConnection: { type: Object },
    isDragging: { type: Boolean },
    draggedNode: { type: Object },
    connectionStart: { type: Object },
    isConnecting: { type: Boolean },
    zoom: { type: Number },
    panX: { type: Number },
    panY: { type: Number }
  };

  static styles = css`
    :host {
      display: block;
      height: 100%;
      width: 100%;
    }

    .builder-container {
      display: flex;
      height: 100%;
      background: #f5f5f5;
    }

    .sidebar {
      width: 300px;
      background: white;
      border-right: 1px solid #e0e0e0;
      display: flex;
      flex-direction: column;
    }

    .sidebar-header {
      padding: 16px;
      border-bottom: 1px solid #e0e0e0;
    }

    .pipeline-name-input {
      width: 100%;
      padding: 8px 12px;
      margin-bottom: 12px;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 500;
    }

    .pipeline-name-input:focus {
      outline: none;
      border-color: #1976d2;
    }

    .sidebar-title {
      font-size: 16px;
      font-weight: 600;
      color: #333;
      margin-bottom: 8px;
    }

    .module-list {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
    }

    .module-item {
      background: #f8f8f8;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      padding: 12px;
      margin-bottom: 8px;
      cursor: move;
      transition: all 0.2s ease;
    }

    .module-item:hover {
      background: #e3f2fd;
      border-color: #1976d2;
      transform: translateX(4px);
    }

    .module-item.dragging {
      opacity: 0.5;
    }

    .module-name {
      font-weight: 500;
      color: #333;
      margin-bottom: 4px;
    }

    .module-type {
      font-size: 12px;
      color: #666;
      background: #e0e0e0;
      padding: 2px 8px;
      border-radius: 3px;
      display: inline-block;
    }

    .canvas-container {
      flex: 1;
      position: relative;
      overflow: hidden;
    }

    .canvas-svg {
      width: 100%;
      height: 100%;
      cursor: grab;
    }

    .canvas-svg.panning {
      cursor: grabbing;
    }

    .grid-pattern {
      fill: none;
      stroke: #e0e0e0;
      stroke-width: 1;
    }

    .node {
      cursor: move;
    }

    .node-rect {
      fill: white;
      stroke: #1976d2;
      stroke-width: 2;
      rx: 8;
    }

    .node.selected .node-rect {
      stroke: #ff9800;
      stroke-width: 3;
    }

    .node-text {
      font-size: 14px;
      font-weight: 500;
      text-anchor: middle;
      dominant-baseline: middle;
      user-select: none;
    }

    .node-port {
      fill: #1976d2;
      stroke: white;
      stroke-width: 2;
      cursor: crosshair;
    }

    .node-port:hover {
      fill: #ff9800;
      r: 8;
    }

    .connection {
      fill: none;
      stroke: #1976d2;
      stroke-width: 2;
      marker-end: url(#arrowhead);
    }

    .connection.selected {
      stroke: #ff9800;
      stroke-width: 3;
    }

    .connection-preview {
      fill: none;
      stroke: #ff9800;
      stroke-width: 2;
      stroke-dasharray: 5,5;
      pointer-events: none;
    }

    .toolbar {
      position: absolute;
      top: 16px;
      right: 16px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 8px;
      display: flex;
      gap: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }

    .toolbar-button {
      width: 36px;
      height: 36px;
      border: 1px solid #e0e0e0;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }

    .toolbar-button:hover {
      background: #f5f5f5;
      border-color: #1976d2;
    }

    .toolbar-button svg {
      width: 20px;
      height: 20px;
    }

    .properties-panel {
      position: absolute;
      bottom: 16px;
      left: 316px;
      right: 16px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 16px;
      box-shadow: 0 -2px 8px rgba(0,0,0,0.1);
      max-height: 200px;
      overflow-y: auto;
    }

    .properties-title {
      font-size: 14px;
      font-weight: 600;
      margin-bottom: 12px;
    }

    .property-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
    }

    .property-label {
      width: 100px;
      font-size: 13px;
      color: #666;
    }

    .property-value {
      flex: 1;
      font-size: 13px;
      color: #333;
    }

    .delete-button {
      background: #f44336;
      color: white;
      border: none;
      padding: 6px 12px;
      border-radius: 4px;
      font-size: 13px;
      cursor: pointer;
      margin-top: 8px;
    }

    .delete-button:hover {
      background: #d32f2f;
    }

  `;

  constructor() {
    super();
    this.pipeline = {
      name: 'New Pipeline',
      nodes: [],
      connections: []
    };
    this.availableModules = [];
    this.selectedNode = null;
    this.selectedConnection = null;
    this.isDragging = false;
    this.draggedNode = null;
    this.connectionStart = null;
    this.isConnecting = false;
    this.zoom = 1;
    this.panX = 0;
    this.panY = 0;
  }

  connectedCallback() {
    super.connectedCallback();
    this.fetchAvailableModules();
    
    // If we have an existing pipeline, convert it to visual format
    if (this.pipeline && this.pipeline.pipelineSteps) {
      console.log('Loading existing pipeline:', this.pipeline);
      this.loadExistingPipeline();
    }
  }

  updated(changedProperties) {
    super.updated(changedProperties);
    
    // If pipeline property changed and it has pipelineSteps, load it
    if (changedProperties.has('pipeline') && this.pipeline && this.pipeline.pipelineSteps) {
      console.log('Pipeline property changed, loading:', this.pipeline);
      this.loadExistingPipeline();
    }
  }

  loadExistingPipeline() {
    // Convert backend pipeline format to visual nodes and connections
    const nodes = [];
    const connections = [];
    const nodeMap = new Map();
    
    // Create nodes from pipeline steps
    Object.entries(this.pipeline.pipelineSteps || {}).forEach(([stepName, stepConfig], index) => {
      const node = {
        id: `node-${stepName}`,
        module: stepConfig.processorInfo?.processorId || stepName,
        type: stepConfig.stepType || 'PIPELINE',
        x: 100 + (index % 3) * 200,
        y: 100 + Math.floor(index / 3) * 100,
        width: 150,
        height: 60,
        inputs: 1,
        outputs: 1
      };
      nodes.push(node);
      nodeMap.set(stepName, node);
    });
    
    // Create connections based on outputs configuration
    Object.entries(this.pipeline.pipelineSteps || {}).forEach(([stepName, stepConfig]) => {
      const fromNode = nodeMap.get(stepName);
      if (fromNode && stepConfig.outputs) {
        Object.values(stepConfig.outputs).forEach(outputStep => {
          const toNode = nodeMap.get(outputStep);
          if (toNode) {
            connections.push({
              id: `conn-${fromNode.id}-${toNode.id}`,
              from: fromNode.id,
              to: toNode.id
            });
          }
        });
      }
    });
    
    this.pipeline = {
      name: this.pipeline.name || 'Untitled Pipeline',
      nodes,
      connections
    };
  }

  async fetchAvailableModules() {
    try {
      const response = await fetch('/api/v1/dev/modules/available');
      if (response.ok) {
        const modules = await response.json();
        this.availableModules = modules.map(m => ({
          name: m.name,
          type: m.type || 'processor',
          description: m.description
        }));
      }
    } catch (err) {
      console.error('Failed to fetch modules:', err);
    }
  }

  handleModuleDragStart(e, module) {
    console.log('Drag start for module:', module);
    e.dataTransfer.effectAllowed = 'copy';
    // Use text/plain to ensure compatibility
    e.dataTransfer.setData('text/plain', JSON.stringify(module));
    // Store in instance for backup
    this.draggedModule = module;
  }

  handleCanvasDragOver(e) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
  }

  handleCanvasDrop(e) {
    e.preventDefault();
    // Try text/plain first
    let moduleData = e.dataTransfer.getData('text/plain');
    console.log('Drop event - module data:', moduleData);
    
    // If no data, try using the stored module
    if (!moduleData && this.draggedModule) {
      console.log('Using stored module:', this.draggedModule);
      const rect = this.shadowRoot.querySelector('.canvas-svg').getBoundingClientRect();
      const x = (e.clientX - rect.left - this.panX) / this.zoom;
      const y = (e.clientY - rect.top - this.panY) / this.zoom;
      
      this.addNode(this.draggedModule, x, y);
      this.draggedModule = null;
      return;
    }
    
    if (moduleData) {
      try {
        const module = JSON.parse(moduleData);
        const rect = this.shadowRoot.querySelector('.canvas-svg').getBoundingClientRect();
        const x = (e.clientX - rect.left - this.panX) / this.zoom;
        const y = (e.clientY - rect.top - this.panY) / this.zoom;
        
        console.log('Adding node at:', x, y, 'for module:', module);
        this.addNode(module, x, y);
      } catch (err) {
        console.error('Error parsing module data:', err);
      }
    }
  }

  addNode(module, x, y) {
    const node = {
      id: `node-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      module: module.name,
      type: module.type,
      x: x - 75, // Center the node
      y: y - 30,
      width: 150,
      height: 60,
      inputs: 1,
      outputs: 1
    };

    this.pipeline = {
      ...this.pipeline,
      nodes: [...this.pipeline.nodes, node]
    };

    this.notifyPipelineChanged();
  }

  handleNodeMouseDown(e, node) {
    e.stopPropagation();
    this.selectedNode = node;
    this.selectedConnection = null;
    
    const svgPoint = this.getSVGPoint(e);
    this.draggedNode = {
      node,
      offsetX: svgPoint.x - node.x,
      offsetY: svgPoint.y - node.y
    };
  }

  handleConnectionMouseDown(e, connection) {
    e.stopPropagation();
    this.selectedConnection = connection;
    this.selectedNode = null;
  }

  handlePortMouseDown(e, node, isOutput) {
    e.stopPropagation();
    if (isOutput) {
      this.connectionStart = { node, isOutput: true };
      this.isConnecting = true;
    }
  }

  handleCanvasMouseMove(e) {
    if (this.draggedNode) {
      const svgPoint = this.getSVGPoint(e);
      const node = this.draggedNode.node;
      node.x = svgPoint.x - this.draggedNode.offsetX;
      node.y = svgPoint.y - this.draggedNode.offsetY;
      this.requestUpdate();
    }
  }

  handleCanvasMouseUp(e) {
    if (this.isConnecting && this.connectionStart) {
      // Check if we're over an input port
      const target = e.target;
      if (target.classList.contains('node-port') && target.dataset.portType === 'input') {
        const targetNodeId = target.dataset.nodeId;
        const targetNode = this.pipeline.nodes.find(n => n.id === targetNodeId);
        
        if (targetNode && targetNode.id !== this.connectionStart.node.id) {
          this.addConnection(this.connectionStart.node, targetNode);
        }
      }
    }
    
    this.draggedNode = null;
    this.connectionStart = null;
    this.isConnecting = false;
  }

  handleCanvasClick(e) {
    if (e.target === e.currentTarget || e.target.classList.contains('grid-rect')) {
      this.selectedNode = null;
      this.selectedConnection = null;
    }
  }

  addConnection(fromNode, toNode) {
    // Check if connection already exists
    const exists = this.pipeline.connections.some(
      c => c.from === fromNode.id && c.to === toNode.id
    );
    
    if (!exists) {
      const connection = {
        id: `conn-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        from: fromNode.id,
        to: toNode.id
      };
      
      this.pipeline = {
        ...this.pipeline,
        connections: [...this.pipeline.connections, connection]
      };
      
      this.notifyPipelineChanged();
    }
  }

  deleteSelectedNode() {
    if (this.selectedNode) {
      this.pipeline = {
        ...this.pipeline,
        nodes: this.pipeline.nodes.filter(n => n.id !== this.selectedNode.id),
        connections: this.pipeline.connections.filter(
          c => c.from !== this.selectedNode.id && c.to !== this.selectedNode.id
        )
      };
      this.selectedNode = null;
      this.notifyPipelineChanged();
    }
  }

  deleteSelectedConnection() {
    if (this.selectedConnection) {
      this.pipeline = {
        ...this.pipeline,
        connections: this.pipeline.connections.filter(c => c.id !== this.selectedConnection.id)
      };
      this.selectedConnection = null;
      this.notifyPipelineChanged();
    }
  }

  // Removed client-side validation - let the backend handle it
  notifyPipelineChanged() {
    // Just emit that the pipeline changed, no validation
    const event = new CustomEvent('pipeline-changed', {
      detail: {
        pipeline: this.pipeline
      }
    });
    this.dispatchEvent(event);
  }

  getSVGPoint(e) {
    const svg = this.shadowRoot.querySelector('.canvas-svg');
    const pt = svg.createSVGPoint();
    pt.x = e.clientX;
    pt.y = e.clientY;
    const screenCTM = svg.getScreenCTM();
    return pt.matrixTransform(screenCTM.inverse());
  }

  zoomIn() {
    this.zoom = Math.min(this.zoom * 1.2, 3);
  }

  zoomOut() {
    this.zoom = Math.max(this.zoom / 1.2, 0.3);
  }

  resetView() {
    this.zoom = 1;
    this.panX = 0;
    this.panY = 0;
  }

  clearPipeline() {
    if (confirm('Are you sure you want to clear the pipeline?')) {
      this.pipeline = {
        name: 'New Pipeline',
        nodes: [],
        connections: []
      };
      this.selectedNode = null;
      this.selectedConnection = null;
    }
  }

  savePipeline() {
    // Convert our visual pipeline to the DTO format expected by the API
    const pipelineDTO = this.convertToPipelineDTO();
    
    const event = new CustomEvent('save-pipeline', {
      detail: { pipeline: pipelineDTO },
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  convertToPipelineDTO() {
    // Create a map of node connections for easy lookup
    const nodeConnections = new Map();
    this.pipeline.nodes.forEach(node => {
      nodeConnections.set(node.id, {
        inputs: [],
        outputs: []
      });
    });
    
    // Build connection map
    this.pipeline.connections.forEach(conn => {
      nodeConnections.get(conn.from).outputs.push(conn.to);
      nodeConnections.get(conn.to).inputs.push(conn.from);
    });
    
    // Convert nodes to steps in topological order
    const steps = this.getTopologicalOrder().map((node, index) => {
      return {
        name: `step-${index + 1}-${node.module}`,
        module: node.module,
        config: {
          // Add any node-specific configuration here
          position: { x: node.x, y: node.y },
          nodeId: node.id
        }
      };
    });
    
    return {
      name: this.pipeline.name,
      description: `Pipeline with ${this.pipeline.nodes.length} modules`,
      steps: steps
    };
  }

  getTopologicalOrder() {
    // Perform topological sort to get proper execution order
    const visited = new Set();
    const result = [];
    
    const visit = (nodeId) => {
      if (visited.has(nodeId)) return;
      visited.add(nodeId);
      
      const node = this.pipeline.nodes.find(n => n.id === nodeId);
      if (!node) return;
      
      // Visit all nodes that this node depends on first
      const incomingConnections = this.pipeline.connections
        .filter(c => c.to === nodeId)
        .map(c => c.from);
      
      incomingConnections.forEach(visit);
      
      result.push(node);
    };
    
    // Start with nodes that have no incoming connections
    this.pipeline.nodes.forEach(node => {
      const hasIncoming = this.pipeline.connections.some(c => c.to === node.id);
      if (!hasIncoming) {
        visit(node.id);
      }
    });
    
    // Visit any remaining nodes (in case of disconnected components)
    this.pipeline.nodes.forEach(node => visit(node.id));
    
    return result;
  }

  renderNode(node) {
    const isSelected = this.selectedNode?.id === node.id;
    
    return html`
      <g class="node ${isSelected ? 'selected' : ''}"
         transform="translate(${node.x}, ${node.y})"
         @mousedown=${(e) => this.handleNodeMouseDown(e, node)}>
        <rect class="node-rect"
              width="${node.width}"
              height="${node.height}" />
        <text class="node-text"
              x="${node.width / 2}"
              y="${node.height / 2}">
          ${node.module}
        </text>
        
        <!-- Input port -->
        <circle class="node-port"
                cx="0"
                cy="${node.height / 2}"
                r="6"
                data-node-id="${node.id}"
                data-port-type="input" />
        
        <!-- Output port -->
        <circle class="node-port"
                cx="${node.width}"
                cy="${node.height / 2}"
                r="6"
                data-node-id="${node.id}"
                data-port-type="output"
                @mousedown=${(e) => this.handlePortMouseDown(e, node, true)} />
      </g>
    `;
  }

  renderConnection(connection) {
    const fromNode = this.pipeline.nodes.find(n => n.id === connection.from);
    const toNode = this.pipeline.nodes.find(n => n.id === connection.to);
    
    if (!fromNode || !toNode) return '';
    
    const x1 = fromNode.x + fromNode.width;
    const y1 = fromNode.y + fromNode.height / 2;
    const x2 = toNode.x;
    const y2 = toNode.y + toNode.height / 2;
    
    // Calculate control points for smooth curve
    const dx = x2 - x1;
    const cp1x = x1 + dx * 0.5;
    const cp2x = x2 - dx * 0.5;
    
    const isSelected = this.selectedConnection?.id === connection.id;
    
    return html`
      <path class="connection ${isSelected ? 'selected' : ''}"
            d="M ${x1},${y1} C ${cp1x},${y1} ${cp2x},${y2} ${x2},${y2}"
            @mousedown=${(e) => this.handleConnectionMouseDown(e, connection)} />
    `;
  }

  renderConnectionPreview() {
    if (!this.isConnecting || !this.connectionStart) return '';
    
    const fromNode = this.connectionStart.node;
    const x1 = fromNode.x + fromNode.width;
    const y1 = fromNode.y + fromNode.height / 2;
    
    // For now, just show a static preview line
    return html`
      <line class="connection-preview"
            x1="${x1}"
            y1="${y1}"
            x2="${x1 + 100}"
            y2="${y1}" />
    `;
  }

  render() {
    return html`
      <div class="builder-container">
        <div class="sidebar">
          <div class="sidebar-header">
            <input 
              type="text"
              class="pipeline-name-input"
              placeholder="Pipeline Name"
              .value=${this.pipeline.name}
              @input=${(e) => this.pipeline = {...this.pipeline, name: e.target.value}}>
            <div class="sidebar-title">Available Modules</div>
            <div style="font-size: 12px; color: #666;">Drag modules to canvas</div>
          </div>
          <div class="module-list">
            ${this.availableModules.map(module => html`
              <div class="module-item"
                   draggable="true"
                   @dragstart=${(e) => this.handleModuleDragStart(e, module)}>
                <div class="module-name">${module.name}</div>
                <span class="module-type">${module.type}</span>
              </div>
            `)}
          </div>
        </div>
        
        <div class="canvas-container"
             @dragover=${this.handleCanvasDragOver}
             @drop=${this.handleCanvasDrop}>
          <svg class="canvas-svg"
               @click=${this.handleCanvasClick}
               @mousemove=${this.handleCanvasMouseMove}
               @mouseup=${this.handleCanvasMouseUp}
               @mouseleave=${this.handleCanvasMouseUp}>
            <defs>
              <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#f0f0f0" stroke-width="1"/>
              </pattern>
              <marker id="arrowhead" markerWidth="10" markerHeight="10" 
                      refX="9" refY="3" orient="auto">
                <polygon points="0 0, 10 3, 0 6" fill="#1976d2" />
              </marker>
            </defs>
            
            <rect width="100%" height="100%" fill="url(#grid)" class="grid-rect" />
            
            <g transform="translate(${this.panX}, ${this.panY}) scale(${this.zoom})">
              <!-- Connections -->
              ${this.pipeline.connections.map(conn => this.renderConnection(conn))}
              
              <!-- Connection preview -->
              ${this.renderConnectionPreview()}
              
              <!-- Nodes -->
              ${this.pipeline.nodes.map(node => this.renderNode(node))}
            </g>
          </svg>
          
          <div class="toolbar">
            <button class="toolbar-button" @click=${this.zoomIn} title="Zoom In">
              <svg fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/>
                <path fill-rule="evenodd" d="M8 6a1 1 0 011 1v1h1a1 1 0 110 2H9v1a1 1 0 11-2 0V9H6a1 1 0 110-2h1V7a1 1 0 011-1z" clip-rule="evenodd"/>
              </svg>
            </button>
            <button class="toolbar-button" @click=${this.zoomOut} title="Zoom Out">
              <svg fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/>
                <path fill-rule="evenodd" d="M6 8a1 1 0 011-1h2a1 1 0 110 2H7a1 1 0 01-1-1z" clip-rule="evenodd"/>
              </svg>
            </button>
            <button class="toolbar-button" @click=${this.resetView} title="Reset View">
              <svg fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd"/>
              </svg>
            </button>
            <button class="toolbar-button" @click=${this.clearPipeline} title="Clear Pipeline">
              <svg fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm4 0a1 1 0 012 0v6a1 1 0 11-2 0V8z" clip-rule="evenodd"/>
              </svg>
            </button>
            <button class="toolbar-button" @click=${this.savePipeline} title="Save Pipeline" style="background: #4caf50; color: white; border-color: #4caf50;">
              <svg fill="currentColor" viewBox="0 0 20 20">
                <path d="M7.707 9.293a1 1 0 00-1.414 1.414l3 3a1 1 0 001.414 0l7-7a1 1 0 00-1.414-1.414L10 11.586 7.707 9.293z"/>
              </svg>
            </button>
          </div>
          
          
          ${this.selectedNode || this.selectedConnection ? html`
            <div class="properties-panel">
              ${this.selectedNode ? html`
                <div class="properties-title">Node Properties</div>
                <div class="property-row">
                  <span class="property-label">Module:</span>
                  <span class="property-value">${this.selectedNode.module}</span>
                </div>
                <div class="property-row">
                  <span class="property-label">Type:</span>
                  <span class="property-value">${this.selectedNode.type}</span>
                </div>
                <div class="property-row">
                  <span class="property-label">ID:</span>
                  <span class="property-value" style="font-family: monospace; font-size: 11px;">${this.selectedNode.id}</span>
                </div>
                <button class="delete-button" @click=${this.deleteSelectedNode}>
                  Delete Node
                </button>
              ` : ''}
              
              ${this.selectedConnection ? html`
                <div class="properties-title">Connection Properties</div>
                <div class="property-row">
                  <span class="property-label">From:</span>
                  <span class="property-value">${this.pipeline.nodes.find(n => n.id === this.selectedConnection.from)?.module}</span>
                </div>
                <div class="property-row">
                  <span class="property-label">To:</span>
                  <span class="property-value">${this.pipeline.nodes.find(n => n.id === this.selectedConnection.to)?.module}</span>
                </div>
                <button class="delete-button" @click=${this.deleteSelectedConnection}>
                  Delete Connection
                </button>
              ` : ''}
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }
}

customElements.define('pipeline-builder', PipelineBuilder);