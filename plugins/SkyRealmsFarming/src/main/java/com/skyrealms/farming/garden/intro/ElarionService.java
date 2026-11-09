package com.skyrealms.farming.garden.intro;

import com.skyrealms.farming.garden.item.GardenItemFactory;
import com.skyrealms.farming.garden.quest.FarmingQuestService;
import com.skyrealms.farming.repository.FarmingRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service managing Elarion the Botanist's welcome dialogue, starter kit delivery,
 * and First Harvest quest onboarding for new Garden visitors.
 */
public final class ElarionService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final FarmingQuestService questService;
    private final Logger logger;

    private final Set<UUID> introCompletedCache = ConcurrentHashMap.newKeySet();

    public ElarionService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull FarmingQuestService questService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.questService = Objects.requireNonNull(questService, "questService cannot be null");
        this.logger = plugin.getLogger();
    }

    public boolean hasCompletedIntro(@NotNull UUID playerUuid) {
        if (introCompletedCache.contains(playerUuid)) {
            return true;
        }

        try {
            boolean completed = repository.hasCompletedElarionIntro(playerUuid);
            if (completed) {
                introCompletedCache.add(playerUuid);
            }
            return completed;
        } catch (SQLException e) {
            logger.warning("Could not check Elarion intro status for player " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    public void resetIntro(@NotNull UUID playerUuid) {
        introCompletedCache.remove(playerUuid);
        try {
            repository.setElarionIntroCompleted(playerUuid, false);
            logger.info("Reset Elarion introduction for player " + playerUuid);
        } catch (SQLException e) {
            logger.warning("Failed to reset Elarion intro for player " + playerUuid + ": " + e.getMessage());
        }
    }

    public void triggerIntro(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (hasCompletedIntro(uuid)) {
            return;
        }

        introCompletedCache.add(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.setElarionIntroCompleted(uuid, true);
            } catch (SQLException e) {
                logger.warning("Failed to save Elarion intro completion for " + player.getName() + ": " + e.getMessage());
            }
        });

        // 1. Play Welcome Sound & Title (compact & clean)
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        Title title = Title.title(
                Component.text("GARDEN CONSERVATORY", TextColor.fromHexString("#FFB7C5"), TextDecoration.BOLD),
                Component.text("Elarion the Botanist approaches you...", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))
        );
        player.showTitle(title);

        // 2. Deliver Starter Supplies
        ItemStack hoe = GardenItemFactory.createNoviceHoe();
        ItemStack seeds = new ItemStack(Material.WHEAT_SEEDS, 16);
        ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 8);

        giveItemOrDrop(player, hoe);
        giveItemOrDrop(player, seeds);
        giveItemOrDrop(player, boneMeal);

        // 3. Start First Harvest Quest
        questService.startQuest(uuid, FarmingQuestService.QUEST_FIRST_HARVEST);

        // 4. Send Rich Tutorial Dialogue (clean vanilla-friendly formatting)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Component dialogue = Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.newline())
                    .append(Component.text("[Elarion] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("\"Welcome beneath the glass dome of our Botanical Conservatory!\"", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("How to progress your agricultural sanctuary:", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("  1. ", NamedTextColor.GRAY)).append(Component.text("16 Crop Plots: ", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("Each plot is a 64x64 zone for your crops.", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("  2. ", NamedTextColor.GRAY)).append(Component.text("Natural Farming: ", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text("Right-click mature crops to fast-harvest & auto-replant!", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("  3. ", NamedTextColor.GRAY)).append(Component.text("Crop Mastery: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("Harvests level up your crop mastery and double drop perks.", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("◆ Starter Supplies Received:", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("  - 1x Novice Cultivator Hoe", NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("  - 16x Wheat Seeds", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("  - 8x Bone Meal", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("◆ Active Quest: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("First Harvest (/garden quest)", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE));

            player.sendMessage(dialogue);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }, 20L);
    }

    private void giveItemOrDrop(@NotNull Player player, @NotNull ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }
}
