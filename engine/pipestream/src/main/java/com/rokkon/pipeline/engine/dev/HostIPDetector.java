package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Network;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * Detects the host IP address that Docker containers can use to reach the host machine.
 * Tries multiple strategies in order:
 * 1. Docker Desktop special hostname (host.docker.internal)
 * 2. Docker bridge gateway IP
 * 3. Fallback to common Docker bridge IP
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class HostIPDetector {
    
    private static final Logger LOG = Logger.getLogger(HostIPDetector.class);
    private static final String DOCKER_DESKTOP_HOST = "host.docker.internal";
    private static final String DEFAULT_DOCKER_BRIDGE = "172.17.0.1";
    
    @Inject
    DevModeDockerClientManager dockerClientManager;
    
    private String detectedHostIP;
    
    /**
     * Detects the host IP address using multiple strategies.
     * Caches the result after first successful detection.
     */
    public String detectHostIP() {
        if (detectedHostIP != null) {
            return detectedHostIP;
        }
        
        // Strategy 1: Check if host.docker.internal resolves (Docker Desktop)
        if (isDockerDesktop()) {
            detectedHostIP = DOCKER_DESKTOP_HOST;
            LOG.infof("Detected Docker Desktop environment, using %s", detectedHostIP);
            return detectedHostIP;
        }
        
        // Strategy 2: Get Docker bridge gateway IP
        Optional<String> bridgeIP = getDockerBridgeGatewayIP();
        if (bridgeIP.isPresent()) {
            detectedHostIP = bridgeIP.get();
            LOG.infof("Detected Docker bridge gateway IP: %s", detectedHostIP);
            return detectedHostIP;
        }
        
        // Strategy 3: Fallback to common Docker bridge IP
        detectedHostIP = DEFAULT_DOCKER_BRIDGE;
        LOG.warnf("Could not detect Docker bridge IP, falling back to default: %s", detectedHostIP);
        return detectedHostIP;
    }
    
    /**
     * Checks if running on Docker Desktop by attempting to resolve host.docker.internal
     */
    private boolean isDockerDesktop() {
        try {
            InetAddress.getByName(DOCKER_DESKTOP_HOST);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets the gateway IP of the Docker bridge network
     */
    private Optional<String> getDockerBridgeGatewayIP() {
        try {
            List<Network> networks = dockerClientManager.getDockerClient().listNetworksCmd().exec();
            
            // Look for the default bridge network
            for (Network network : networks) {
                if ("bridge".equals(network.getDriver()) && network.getName() != null && 
                    (network.getName().equals("bridge") || network.getName().endsWith("_default"))) {
                    
                    Network.Ipam ipam = network.getIpam();
                    if (ipam != null && ipam.getConfig() != null && !ipam.getConfig().isEmpty()) {
                        String gateway = ipam.getConfig().get(0).getGateway();
                        if (gateway != null && !gateway.isEmpty()) {
                            return Optional.of(gateway);
                        }
                    }
                }
            }
            
            // Try to get from host network interface
            return getHostIPFromNetworkInterface();
            
        } catch (Exception e) {
            LOG.error("Failed to detect Docker bridge gateway IP", e);
            return Optional.empty();
        }
    }
    
    /**
     * Attempts to find Docker bridge IP from host network interfaces
     */
    private Optional<String> getHostIPFromNetworkInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // Look for docker0 interface
                if (ni.getName().equals("docker0")) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                            return Optional.of(addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not find docker0 interface", e);
        }
        return Optional.empty();
    }
    
    /**
     * Resets the cached host IP, forcing re-detection on next call
     */
    public void resetCache() {
        detectedHostIP = null;
    }
}