package com.skyrealms.platform.integrations;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface contract for external permissions and chat format bridges (e.g. LuckPerms adapter).
 */
public interface PermissionsProvider {

    boolean hasPermission(@NotNull UUID playerId, @NotNull String permission);

    @NotNull
    Optional<String> getPrefix(@NotNull UUID playerId);

    @NotNull
    Optional<String> getPrimaryGroup(@NotNull UUID playerId);
}
