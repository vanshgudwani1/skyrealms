package com.skyrealms.platform.common.service;

import com.skyrealms.platform.api.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe concurrent implementation of {@link ServiceRegistry}.
 */
public final class ConcurrentServiceRegistry implements ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(@NotNull Class<T> serviceClass, @NotNull T serviceInstance) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        Objects.requireNonNull(serviceInstance, "serviceInstance cannot be null");
        if (!serviceClass.isInstance(serviceInstance)) {
            throw new IllegalArgumentException(
                    "Service instance " + serviceInstance.getClass().getName() +
                            " does not implement " + serviceClass.getName()
            );
        }
        services.put(serviceClass, serviceInstance);
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        Object instance = services.get(serviceClass);
        if (instance == null) {
            throw new IllegalStateException("Service not registered: " + serviceClass.getName());
        }
        return (T) instance;
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional(@NotNull Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        return Optional.ofNullable((T) services.get(serviceClass));
    }

    @Override
    public <T> boolean unregister(@NotNull Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        return services.remove(serviceClass) != null;
    }

    @Override
    public boolean isRegistered(@NotNull Class<?> serviceClass) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        return services.containsKey(serviceClass);
    }

    public void clear() {
        services.clear();
    }
}
