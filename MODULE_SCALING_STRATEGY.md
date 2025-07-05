# Module Scaling Strategy

## Current Approach (Port-per-instance)
- Instance 1: Port 39100
- Instance 2: Port 39101  
- Instance 3: Port 39102
- Problems: Port exhaustion, no load balancing, requires client-side logic

## Recommended Approach (Single exposed port with internal scaling)

### Architecture
```
Host Machine
├── Module Instance 1 (port 39100 exposed to host)
├── Module Instance 2 (internal only, no host port)
├── Module Instance 3 (internal only, no host port)
└── Consul (service discovery & health checks)
```

### Benefits
1. **Single entry point** - Clients always connect to port 39100
2. **Internal load balancing** - Consul/engine handles routing
3. **No port conflicts** - Additional instances don't need host ports
4. **Simpler client code** - No need to track instance ports

### Implementation Options

#### Option 1: Consul Connect (Service Mesh)
- Use Consul's built-in proxy for load balancing
- Automatic mTLS between services
- Most complex but most feature-rich

#### Option 2: Engine-based routing
- Engine queries Consul for healthy instances
- Engine load balances requests across instances
- Simpler but requires engine changes

#### Option 3: Docker internal networking only
- Only first instance has host port
- All instances register in Consul with Docker internal IPs
- Engine connects to instances via Docker network
- Current approach we're implementing

### Network Layout
```
echo-module-1: 172.17.0.2:39100 -> Host:39100 (exposed)
echo-module-2: 172.17.0.3:39100 -> No host port
echo-module-3: 172.17.0.4:39100 -> No host port

All register in Consul as:
- echo-module-1 at host.docker.internal:39100
- echo-module-2 at 172.17.0.3:39100  
- echo-module-3 at 172.17.0.4:39100
```