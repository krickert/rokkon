# CRITICAL DEV MODE REQUIREMENTS - DO NOT SIMPLIFY

## The Problem We Keep Hitting

Every time we try to implement dev mode, we hit the same cycle:
1. Try to implement two-container Consul setup (server + agent)
2. Hit networking issues with host networking
3. Someone suggests "let me try a simpler approach" 
4. Remove the agent and connect directly to server
5. Realize this breaks health checks and service discovery
6. Go back to step 1

## Why The "Simple" Approach DOES NOT WORK

**The simplified single-container approach (Quarkus connecting directly to Consul server on port 38500) FAILS because:**

1. **Health Checks Break**: Consul server (in Docker) cannot reach Quarkus (on host) to perform health checks
2. **Service Registration Fails**: When modules register, Consul can't verify they're actually running
3. **Network Isolation**: Docker container can't access host services without proper networking
4. **Breaks Production Parity**: Production uses sidecar pattern - dev mode should mirror this

## Why We MUST Have Two Containers

### Container 1: Consul Server
- Runs in Docker network
- Provides UI on port 38500
- Stores configuration
- Acts as the central coordination point

### Container 2: Consul Agent (Sidecar)
- MUST use host networking
- Provides localhost:8500 for Quarkus to connect to
- Bridges between host (where Quarkus runs) and Docker network (where server runs)
- Allows health checks to work (agent can reach both Quarkus AND server)

## The Networking Challenge

The core issue we keep hitting:
- Agent with host networking binds to 127.0.0.1
- Server is at 172.17.0.x (Docker network)
- Agent can't route from 127.0.0.1 to 172.17.0.x
- Error: "dial tcp 127.0.0.1:0->172.17.0.2:8300: connect: invalid argument"

## Solutions We've Tried That Failed

1. **Binding agent to 0.0.0.0**: Still had routing issues
2. **Using different ports**: Doesn't solve the fundamental networking problem
3. **Removing the agent**: Breaks health checks (THIS IS NOT A SOLUTION)
4. **Static initializers**: Quarkus config loads too early
5. **Custom ConfigSource**: Too complex and still loads too early

## Why Dev Mode is Different from Docker Compose

The working docker-compose.yml pattern shows:
- Sidecar and app share network namespace (`network_mode: "service:xxx"`)
- Both are in containers
- Sidecar exposes the app ports

But in dev mode:
- Quarkus runs on the HOST (not in a container)
- We can't use `network_mode: "service:xxx"`
- The agent MUST use host networking to provide localhost:8500

## The Dev Mode Challenge

We need an agent that bridges:
1. Host network (where Quarkus runs)
2. Docker network (where Consul server runs)

This is fundamentally different from the container-to-container pattern.

## The Correct Solution Path

For dev mode specifically, we need:
1. Consul server in Docker (port 38500)
2. Consul agent with host networking that:
   - Binds to localhost:8500 for Quarkus
   - Can reach the server in Docker network
   - Can health check services on the host

The networking issue we hit:
- Agent with `-bind=127.0.0.1` can't route to Docker network
- Agent with `-bind=0.0.0.0` fails with host networking
- We need proper advertise addresses or different network configuration

## What NOT to Do

1. **DO NOT remove the agent** - it's required for health checks
2. **DO NOT suggest "simpler approach"** - we've been down this road
3. **DO NOT ignore that modules need to register** - they need the sidecar pattern too
4. **DO NOT forget production parity** - dev mode should work like production

## Next Steps

We need to solve the agent networking issue properly:
1. The agent needs to join the server 
2. The agent needs to handle health checks for services on the host
3. The agent needs to be reachable at localhost:8500

This is complex because it IS complex. The sidecar pattern requires careful networking setup.