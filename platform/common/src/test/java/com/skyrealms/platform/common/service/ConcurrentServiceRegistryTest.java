package com.skyrealms.platform.common.service;

import com.skyrealms.platform.api.service.ServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentServiceRegistryTest {

    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConcurrentServiceRegistry();
    }

    interface GreetingService {
        String greet(String name);
    }

    static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    @Test
    @DisplayName("Should register and retrieve service successfully")
    void shouldRegisterAndRetrieveService() {
        GreetingService service = new DefaultGreetingService();
        registry.register(GreetingService.class, service);

        assertThat(registry.isRegistered(GreetingService.class)).isTrue();
        assertThat(registry.get(GreetingService.class)).isSameAs(service);
        assertThat(registry.get(GreetingService.class).greet("SkyRealms")).isEqualTo("Hello, SkyRealms");
    }

    @Test
    @DisplayName("Should return Optional when using getOptional")
    void shouldReturnOptional() {
        assertThat(registry.getOptional(GreetingService.class)).isEmpty();

        GreetingService service = new DefaultGreetingService();
        registry.register(GreetingService.class, service);

        Optional<GreetingService> optional = registry.getOptional(GreetingService.class);
        assertThat(optional).isPresent().containsSame(service);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when required service is missing")
    void shouldThrowWhenServiceMissing() {
        assertThatThrownBy(() -> registry.get(GreetingService.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Service not registered");
    }

    @Test
    @DisplayName("Should unregister service")
    void shouldUnregisterService() {
        GreetingService service = new DefaultGreetingService();
        registry.register(GreetingService.class, service);
        assertThat(registry.isRegistered(GreetingService.class)).isTrue();

        boolean removed = registry.unregister(GreetingService.class);
        assertThat(removed).isTrue();
        assertThat(registry.isRegistered(GreetingService.class)).isFalse();
    }
}
