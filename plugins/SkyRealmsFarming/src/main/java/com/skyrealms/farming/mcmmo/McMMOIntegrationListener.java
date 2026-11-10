package com.skyrealms.farming.mcmmo;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent;
import com.gmail.nossr50.events.experience.McMMOPlayerPreXpGainEvent;
import com.gmail.nossr50.events.skills.McMMOPlayerNotificationEvent;
import com.gmail.nossr50.events.skills.abilities.McMMOPlayerAbilityActivateEvent;
import com.gmail.nossr50.events.skills.secondaryabilities.SubSkillEvent;
import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenAbility;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces single-skill mcMMO integration:
 * 1. Cancels all XP gain for every skill except Herbalism (Farming).
 * 2. Restricts Farming XP strictly to authorized, unlocked SkyRealms Garden regions.
 * 3. Disables all default mcMMO active SuperAbilities and passive SubSkills.
 * 4. Blocks disabled skill commands while preserving /herbalism, /mcstats, /mctop, /mcrank.
 */
public final class McMMOIntegrationListener implements Listener {

    private static final Set<String> DISABLED_SKILL_COMMANDS = Set.of(
            "acrobatics", "alchemy", "archery", "axes", "crossbows", "excavation",
            "fishing", "maces", "mining", "repair", "salvage", "smelting",
            "spears", "swords", "taming", "tridents", "unarmed", "woodcutting", "mcability"
    );

    private static final Set<NotificationType> SUPPRESSED_NOTIFICATIONS = Set.of(
            NotificationType.SUBSKILL_UNLOCKED,
            NotificationType.LEVEL_UP_MESSAGE,
            NotificationType.SUBSKILL_MESSAGE,
            NotificationType.SUBSKILL_MESSAGE_FAILED,
            NotificationType.SUPER_ABILITY,
            NotificationType.SUPER_ABILITY_ALERT_OTHERS,
            NotificationType.ABILITY_OFF,
            NotificationType.ABILITY_COOLDOWN,
            NotificationType.ABILITY_REFRESHED,
            NotificationType.ITEM_MESSAGE
    );

    private final GardenService gardenService;
    private final IslandService islandService;
    private final CropMasteryService masteryService;
    private final GardenAbilityService abilityService;

    public McMMOIntegrationListener(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService,
            @NotNull CropMasteryService masteryService,
            @NotNull GardenAbilityService abilityService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreXpGain(@NotNull McMMOPlayerPreXpGainEvent event) {
        if (event.getSkill() != PrimarySkillType.HERBALISM) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        if (!gardenService.isGardenWorld(player.getWorld())) {
            event.setCancelled(true);
            return;
        }

        Optional<Garden> optGarden = gardenService.getGardenAt(player.getLocation());
        if (optGarden.isEmpty() || !optGarden.get().unlocked()) {
            event.setCancelled(true);
            return;
        }

        if (!player.isOp() && !player.hasPermission("skyrealms.garden.admin")) {
            Optional<Island> optIsland = islandService.getIsland(optGarden.get().islandId());
            if (optIsland.isEmpty() || !optIsland.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAbilityActivate(@NotNull McMMOPlayerAbilityActivateEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSubSkillTrigger(@NotNull SubSkillEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisabledSkillCommand(@NotNull PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        if (!msg.startsWith("/")) return;
        String base = msg.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = base.indexOf(':');
        if (colon >= 0) {
            base = base.substring(colon + 1);
        }
        if (DISABLED_SKILL_COMMANDS.contains(base)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("✦ Only Farming is enabled in SkyRealms! Use /garden mastery or /garden abilities.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMcMMONotification(@NotNull McMMOPlayerNotificationEvent event) {
        if (SUPPRESSED_NOTIFICATIONS.contains(event.getEventNotificationType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmingLevelUp(@NotNull McMMOPlayerLevelUpEvent event) {
        if (event.getSkill() != PrimarySkillType.HERBALISM) {
            return;
        }

        Player player = event.getPlayer();
        int newLevel = event.getSkillLevel();
        int oldLevel = Math.max(0, newLevel - event.getLevelsGained());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.35f);
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("Farming ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("Lv." + oldLevel + " -> Lv." + newLevel, NamedTextColor.YELLOW, TextDecoration.BOLD)));

        for (GardenAbility ability : GardenAbility.values()) {
            if (!abilityService.isUnlocked(ability, oldLevel) && abilityService.isUnlocked(ability, newLevel)) {
                player.sendMessage(Component.text("  ★ Unlocked: ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(ability.displayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" (/garden abilities)", NamedTextColor.DARK_GRAY))
                        .hoverEvent(HoverEvent.showText(Component.text(ability.triggerType() + ": " + ability.shortSummary(), NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/garden abilities")));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
        }

        masteryService.recheckAllMasteries(player, newLevel);
    }
}
