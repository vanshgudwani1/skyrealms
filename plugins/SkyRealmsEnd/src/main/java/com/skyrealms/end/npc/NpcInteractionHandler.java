package com.skyrealms.end.npc;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Callback invoked when a player interacts with an End Village NPC whose action type matches
 * the registered handler (shops, quests, training, wilderness warps, or Dragon license checks).
 */
@FunctionalInterface
public interface NpcInteractionHandler {
    void handle(@NotNull Player player, @NotNull EndNpcDefinition npc);
}

