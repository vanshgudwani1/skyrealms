package com.skyrealms.platform.integrations;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface contract for external economy bridges (e.g. Vault adapter).
 */
public interface EconomyProvider {

    @NotNull
    BigDecimal getBalance(@NotNull UUID playerId, @NotNull String currencyId);

    boolean has(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount);

    void deposit(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount, @NotNull String reason);

    void withdraw(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount, @NotNull String reason);
}
