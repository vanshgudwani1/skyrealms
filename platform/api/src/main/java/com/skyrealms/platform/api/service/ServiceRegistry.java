package com.skyrealms.platform.api.service;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Type-safe service container facilitating loose coupling between modules.
 */
public interface ServiceRegistry {

    /**
     * Registers a service implementation for a given interface contract.
     *
     * @param serviceClass    the service interface class.
     * @param serviceInstance the implementation instance.
     * @param <T>             the service type.
     */
    <T> void register(@NotNull Class<T> serviceClass, @NotNull T serviceInstance);

    /**
     * Retrieves a required registered service.
     *
     * @param serviceClass the service interface class.
     * @param <T>          the service type.
     * @return the registered service instance.
     * @throws IllegalStateException if the service is not registered.
     */
    @NotNull
    <T> T get(@NotNull Class<T> serviceClass);

    /**
     * Retrieves an optional registered service.
     *
     * @param serviceClass the service interface class.
     * @param <T>          the service type.
     * @return an Optional containing the service if registered.
     */
    @NotNull
    <T> Optional<T> getOptional(@NotNull Class<T> serviceClass);

    /**
     * Unregisters a service.
     *
     * @param serviceClass the service interface class.
     * @param <T>          the service type.
     * @return true if a service was unregistered, false otherwise.
     */
    <T> boolean unregister(@NotNull Class<T> serviceClass);

    /**
     * Checks if a service is currently registered.
     *
     * @param serviceClass the service interface class.
     * @return true if present.
     */
    boolean isRegistered(@NotNull Class<?> serviceClass);
}
