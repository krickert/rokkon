package com.rokkon.pipeline.registration;

import com.rokkon.search.grpc.ModuleInfo;
import io.quarkus.test.Mock;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Mock
@ApplicationScoped
public class MockConsulModuleRegistry extends ConsulModuleRegistry {
    
    private static final Logger LOG = Logger.getLogger(MockConsulModuleRegistry.class);
    
    private final Map<String, ModuleInfo> consulServices = new ConcurrentHashMap<>();
    
    @Override
    public Uni<String> registerService(ModuleInfo moduleInfo) {
        LOG.infof("🧪 Mock registering service: %s at %s:%d", 
                moduleInfo.getServiceName(), moduleInfo.getHost(), moduleInfo.getPort());
                
        String consulServiceId = "grpc-" + moduleInfo.getServiceId();
        consulServices.put(consulServiceId, moduleInfo);
        
        LOG.infof("✅ Mock service registered successfully: %s -> %s", 
                moduleInfo.getServiceName(), consulServiceId);
                
        return Uni.createFrom().item(consulServiceId);
    }
    
    @Override
    public Uni<Boolean> unregisterService(String consulServiceId) {
        LOG.infof("🧪 Mock unregistering service: %s", consulServiceId);
        
        ModuleInfo removed = consulServices.remove(consulServiceId);
        if (removed != null) {
            LOG.infof("✅ Mock service unregistered successfully: %s", consulServiceId);
            return Uni.createFrom().item(true);
        } else {
            LOG.warnf("⚠️ Mock service not found for unregistration: %s", consulServiceId);
            return Uni.createFrom().item(false);
        }
    }
    
    @Override
    public Uni<Boolean> checkServiceHealth(String consulServiceId) {
        LOG.debugf("🧪 Mock checking service health: %s", consulServiceId);
        
        ModuleInfo module = consulServices.get(consulServiceId);
        boolean isHealthy = module != null;
        
        LOG.debugf("🧪 Mock health check result for %s: %s", consulServiceId, isHealthy);
        
        return Uni.createFrom().item(isHealthy);
    }
}