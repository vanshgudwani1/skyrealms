package com.skyrealms.farming.event;

import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.farming.player.levelup.v1
 */
public final class FarmingLevelUpEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.farming.player.levelup.v1";

    private final Player player;
    private final int oldLevel;
    private final int newLevel;
    private final Instant timestamp;

    public FarmingLevelUpEvent(@NotNull Player player, int oldLevel, int newLevel) {
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.timestamp = Instant.now();
    }

    @Override
    @NotNull
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    @NotNull
    public Instant timestamp() {
        return timestamp;
    }

    @NotNull
    public Player player() {
        return player;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }
}
