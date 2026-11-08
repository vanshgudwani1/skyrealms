package com.skyrealms.farming.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an upgrade applied to a specific farming generator.
 */
public final class GeneratorUpgrade {

    public static final String KEY_SPEED = "speed";
    public static final String KEY_YIELD = "yield";
    public static final String KEY_CAPACITY = "capacity";
    public static final String KEY_MUTATION = "mutation";
    public static final String KEY_XP_BOOST = "xp_boost";

    private final String key;
    private int level;
    private Instant updatedAt;

    public GeneratorUpgrade(@NotNull String key, int level, @NotNull Instant updatedAt) {
        this.key = Objects.requireNonNull(key, "key cannot be null").toLowerCase();
        this.level = Math.max(1, level);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public String key() {
        return key;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }
}
