package com.skyrealms.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * PlaceholderAPI expansion exposing the {@code %skyrealms_*%} placeholder namespace.
 */
public final class SkyRealmsPlaceholderExpansion extends PlaceholderExpansion {

    private final String version;
    private final SkyRealmsPlaceholderResolver resolver;
    private final List<String> placeholders;

    public SkyRealmsPlaceholderExpansion(
            @NotNull String version,
            @NotNull SkyRealmsPlaceholderResolver resolver
    ) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.placeholders = SkyRealmsPlaceholderResolver.SUPPORTED_KEYS.stream()
                .map(k -> "%skyrealms_" + k + "%")
                .toList();
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "skyrealms";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "SkyRealms Team";
    }

    @Override
    @NotNull
    public String getVersion() {
        return version;
    }

    @Override
    @NotNull
    public String getName() {
        return "SkyRealms";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    @NotNull
    public List<String> getPlaceholders() {
        return placeholders;
    }

    @Override
    @Nullable
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        return resolver.resolve(player, params);
    }
}

