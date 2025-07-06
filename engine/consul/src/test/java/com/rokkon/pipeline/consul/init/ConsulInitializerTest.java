package com.rokkon.pipeline.consul.init;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.consul.events.ConsulStateRestored;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@QuarkusTest
class ConsulInitializerTest {
    
    @Inject
    ConsulInitializer consulInitializer;
    
    @InjectMock
    GlobalModuleRegistryService moduleRegistryService;
    
    @InjectMock
    Event<ConsulStateRestored> stateRestoredEvent;
    
    @Test
    void testRestoreModuleState() {
        // Given
        ModuleRegistration module1 = new ModuleRegistration(
            "echo-module-123",
            "echo-module",
            "localhost",
            39100,
            true,
            null,
            null
        );
        
        ModuleRegistration module2 = new ModuleRegistration(
            "parser-module-456",
            "parser-module",
            "localhost", 
            39101,
            false, // disabled - potential zombie
            null,
            null
        );
        
        Set<ModuleRegistration> modules = Set.of(module1, module2);
        when(moduleRegistryService.listRegisteredModules())
            .thenReturn(Uni.createFrom().item(modules));
        
        // When
        consulInitializer.restoreModuleState().await().indefinitely();
        
        // Then
        verify(moduleRegistryService).listRegisteredModules();
        
        // Verify event was fired
        ArgumentCaptor<ConsulStateRestored> eventCaptor = 
            ArgumentCaptor.forClass(ConsulStateRestored.class);
        verify(stateRestoredEvent).fire(eventCaptor.capture());
        
        ConsulStateRestored event = eventCaptor.getValue();
        assertThat(event.modules()).hasSize(2);
        assertThat(event.modules()).contains(module1, module2);
        
        // Verify restored modules are accessible
        Set<ModuleRegistration> restoredModules = consulInitializer.getRestoredModules();
        assertThat(restoredModules).hasSize(2);
        assertThat(restoredModules).contains(module1, module2);
    }
    
    @Test
    void testRestoreEmptyState() {
        // Given
        when(moduleRegistryService.listRegisteredModules())
            .thenReturn(Uni.createFrom().item(Set.of()));
        
        // When
        consulInitializer.restoreModuleState().await().indefinitely();
        
        // Then
        verify(moduleRegistryService).listRegisteredModules();
        
        // Verify event was fired with empty set
        ArgumentCaptor<ConsulStateRestored> eventCaptor = 
            ArgumentCaptor.forClass(ConsulStateRestored.class);
        verify(stateRestoredEvent).fire(eventCaptor.capture());
        
        ConsulStateRestored event = eventCaptor.getValue();
        assertThat(event.modules()).isEmpty();
    }
    
    @Test
    void testRestoreWithRetry() {
        // Given - first attempt fails, second succeeds
        ModuleRegistration module = new ModuleRegistration(
            "test-module-789",
            "test-module",
            "localhost",
            39102,
            true,
            null,
            null
        );
        
        when(moduleRegistryService.listRegisteredModules())
            .thenReturn(
                Uni.createFrom().failure(new RuntimeException("Connection failed")),
                Uni.createFrom().item(Set.of(module))
            );
        
        // When
        consulInitializer.restoreModuleState().await().indefinitely();
        
        // Then - should have called twice due to retry
        verify(moduleRegistryService, times(2)).listRegisteredModules();
        
        // Verify event was fired after successful retry
        ArgumentCaptor<ConsulStateRestored> eventCaptor = 
            ArgumentCaptor.forClass(ConsulStateRestored.class);
        verify(stateRestoredEvent).fire(eventCaptor.capture());
        
        ConsulStateRestored event = eventCaptor.getValue();
        assertThat(event.modules()).hasSize(1);
        assertThat(event.modules()).contains(module);
    }
}