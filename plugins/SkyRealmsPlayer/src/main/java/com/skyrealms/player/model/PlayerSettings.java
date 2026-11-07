package com.skyrealms.player.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Player preference settings.
 */
public final class PlayerSettings {

    private final boolean scoreboardVisible;
    private final boolean chatVisible;
    private final boolean privateMessagesEnabled;
    private final boolean islandFlyEnabled;
    private final Instant updatedAt;

    public PlayerSettings(
            boolean scoreboardVisible,
            boolean chatVisible,
            boolean privateMessagesEnabled,
            boolean islandFlyEnabled,
            @NotNull Instant updatedAt
    ) {
        this.scoreboardVisible = scoreboardVisible;
        this.chatVisible = chatVisible;
        this.privateMessagesEnabled = privateMessagesEnabled;
        this.islandFlyEnabled = islandFlyEnabled;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public static PlayerSettings createDefault() {
        return new PlayerSettings(true, true, true, false, Instant.now());
    }

    public boolean isScoreboardVisible() {
        return scoreboardVisible;
    }

    public boolean isChatVisible() {
        return chatVisible;
    }

    public boolean isPrivateMessagesEnabled() {
        return privateMessagesEnabled;
    }

    public boolean isIslandFlyEnabled() {
        return islandFlyEnabled;
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    public PlayerSettings withScoreboardVisible(boolean visible) {
        return new PlayerSettings(visible, chatVisible, privateMessagesEnabled, islandFlyEnabled, Instant.now());
    }

    public PlayerSettings withChatVisible(boolean visible) {
        return new PlayerSettings(scoreboardVisible, visible, privateMessagesEnabled, islandFlyEnabled, Instant.now());
    }

    public PlayerSettings withPrivateMessagesEnabled(boolean enabled) {
        return new PlayerSettings(scoreboardVisible, chatVisible, enabled, islandFlyEnabled, Instant.now());
    }

    public PlayerSettings withIslandFlyEnabled(boolean enabled) {
        return new PlayerSettings(scoreboardVisible, chatVisible, privateMessagesEnabled, enabled, Instant.now());
    }
}
