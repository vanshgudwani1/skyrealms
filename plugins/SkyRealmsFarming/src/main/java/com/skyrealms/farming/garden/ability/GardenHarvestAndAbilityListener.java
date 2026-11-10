package com.skyrealms.farming.garden.ability;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.harvest.HarvestRewardContext;
import com.skyrealms.farming.garden.harvest.HarvestRewardPipeline;
import com.skyrealms.farming.garden.harvest.HarvestRewardPlan;
import com.skyrealms.farming.garden.journal.BotanicalJournalService;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.garden.plot.CropBed;
import com.skyrealms.farming.garden.plot.GardenPlotCustomizationService;
import com.skyrealms.farming.garden.plot.PlotEditorService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.integrations.CustomEnchantProvider;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates mature Garden crop harvesting and Sakura Harvest Surge AoE activation,
 * routing every eligible crop through HarvestRewardPipeline with per-block harvest locking.
 */
public final class GardenHarvestAndAbilityListener implements Listener {

    private final GardenService gardenService;
    private final IslandService islandService;
    private final McMMOFarmingService mcMmoService;
    private final GardenAbilityService abilityService;
    private final CropMasteryService masteryService;
    private final BotanicalJournalService journalService;
    private final HarvestRewardPipeline rewardPipeline;
    private final GardenPlotCustomizationService customizationService;
    private final PlotEditorService editorService;

    public GardenHarvestAndAbilityListener(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService,
            @NotNull CropMasteryService masteryService,
            @NotNull BotanicalJournalService journalService,
            @NotNull HarvestRewardPipeline rewardPipeline,
            @NotNull GardenPlotCustomizationService customizationService,
            @NotNull PlotEditorService editorService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.journalService = Objects.requireNonNull(journalService, "journalService cannot be null");
        this.rewardPipeline = Objects.requireNonNull(rewardPipeline, "rewardPipeline cannot be null");
        this.customizationService = Objects.requireNonNull(customizationService, "customizationService cannot be null");
        this.editorService = Objects.requireNonNull(editorService, "editorService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHoeSneakRightClick(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (editorService.isEditing(player.getUniqueId())) return;
        if (!player.isSneaking()) return;
        if (!gardenService.isGardenWorld(player.getWorld())) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isHoe(held.getType())) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(player.getLocation());
        if (optGarden.isEmpty() || !optGarden.get().unlocked()) return;
        if (!hasHarvestPermission(player, optGarden.get())) {
            player.sendActionBar(Component.text("⛔ You lack permission to use Garden abilities here.", NamedTextColor.RED));
            return;
        }

        // Prevent accidental farmland tilling while triggering Surge
        event.setCancelled(true);

        int farmingLevel = mcMmoService.getFarmingLevel(player);
        if (!abilityService.isUnlocked(GardenAbility.SAKURA_HARVEST_SURGE, farmingLevel)) {
            player.sendActionBar(Component.text("✦ Sakura Harvest Surge unlocks at mcMMO Farming Lv."
                    + abilityService.config().surgeUnlockLevel() + "!", NamedTextColor.RED));
            return;
        }

        if (abilityService.isSurgeActive(player.getUniqueId())) {
            long rem = abilityService.getSurgeRemainingActiveSeconds(player.getUniqueId());
            player.sendActionBar(Component.text("✦ Sakura Harvest Surge active (" + rem + "s left)", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        long cooldown = abilityService.getSurgeRemainingCooldownSeconds(player.getUniqueId());
        if (cooldown > 0) {
            player.sendActionBar(Component.text("⏳ Sakura Harvest Surge on cooldown: " + cooldown + "s", NamedTextColor.YELLOW));
            return;
        }

        if (abilityService.tryActivateSurge(player.getUniqueId(), farmingLevel)) {
            int duration = abilityService.getSurgeDurationSeconds(farmingLevel);
            player.playSound(player.getLocation(), Sound.BLOCK_CHERRY_SAPLING_PLACE, 1.0f, 1.3f);
            player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1.0, 0), 25, 0.8, 0.5, 0.8, 0.05);
            player.sendMessage(Component.text("✿ ", TextColor.fromHexString("#FF99CC"))
                    .append(Component.text("Sakura Harvest Surge ACTIVATED! ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text("3x3 Garden Harvest for " + duration + "s!", NamedTextColor.WHITE)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!gardenService.isGardenWorld(block.getWorld())) return;

        CropType cropType = CropType.fromBlockMaterial(block.getType());
        if (cropType == null || !isMature(block)) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optGarden.isEmpty() || optPlot.isEmpty() || !optPlot.get().unlocked()) return;

        Player player = event.getPlayer();
        if (editorService.isEditing(player.getUniqueId())) return;
        Garden garden = optGarden.get();
        if (!garden.unlocked() || !hasHarvestPermission(player, garden)) return;

        int farmingLevel = mcMmoService.getFarmingLevel(player);
        ItemStack tool = player.getInventory().getItemInMainHand();

        // 1. Primary crop harvest through authoritative Reward Pipeline
        boolean primaryProcessed = executeSingleHarvest(player, block, garden, optPlot.get(), cropType, tool, farmingLevel, true, false, true);
        if (!primaryProcessed) {
            return;
        }

        // 2. Sakura Harvest Surge 3x3 AoE (each neighbor independently validated & locked)
        if (abilityService.isSurgeActive(player.getUniqueId())) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block neighbor = block.getRelative(dx, 0, dz);
                    if (!garden.isInRegion(neighbor.getLocation())) continue;

                    CropType neighborCrop = CropType.fromBlockMaterial(neighbor.getType());
                    if (neighborCrop == null || !isMature(neighbor)) continue;

                    Optional<GardenPlot> neighborPlot = gardenService.getPlotAt(neighbor.getLocation());
                    if (neighborPlot.isEmpty() || !neighborPlot.get().unlocked()) continue;

                    if (executeSingleHarvest(player, neighbor, garden, neighborPlot.get(), neighborCrop, tool, farmingLevel, false, true, false)) {
                        BlockData harvestedData = neighbor.getBlockData().clone();
                        List<ItemStack> drops = new ArrayList<>(neighbor.getDrops(tool, player));
                        neighbor.setType(Material.AIR, true);

                        CustomEnchantProvider enchantProvider = org.bukkit.Bukkit.getServicesManager().load(CustomEnchantProvider.class);
                        if (enchantProvider != null) {
                            enchantProvider.handleAoEReplenish(player, tool, neighbor, harvestedData, drops);
                            enchantProvider.deliverOrDrop(
                                    player,
                                    neighbor.getLocation().add(0.5, 0.3, 0.5),
                                    drops,
                                    enchantProvider.hasTelekinesis(tool)
                            );
                        } else {
                            for (ItemStack drop : drops) {
                                if (drop != null && drop.getAmount() > 0) {
                                    neighbor.getWorld().dropItemNaturally(neighbor.getLocation().add(0.5, 0.3, 0.5), drop);
                                }
                            }
                        }
                        neighbor.getWorld().spawnParticle(Particle.CHERRY_LEAVES, neighbor.getLocation().add(0.5, 0.5, 0.5), 4, 0.25, 0.2, 0.25, 0.01);
                    }
                }
            }
        }
    }

    private boolean executeSingleHarvest(
            @NotNull Player player,
            @NotNull Block block,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull CropType cropType,
            @NotNull ItemStack tool,
            int farmingLevel,
            boolean primaryBlockBreak,
            boolean surgeAoE,
            boolean sendFeedback
    ) {
        UUID harvestId = UUID.randomUUID();
        String worldName = block.getWorld().getName();
        int bx = block.getX(), by = block.getY(), bz = block.getZ();

        if (!rewardPipeline.tryClaimBlock(worldName, bx, by, bz, harvestId)) {
            return false;
        }

        try {
            List<ItemStack> baseDrops = new ArrayList<>(block.getDrops(tool, player));
            CropMastery mastery = masteryService.getMastery(player.getUniqueId(), cropType);
            Optional<CropBed> optBed = customizationService.getBedAtWorldCoords(garden, plot, bx, bz);
            double bedDropModifier = optBed.map(b -> b.getBonusDropModifier(cropType)).orElse(0.0);
            double bedXpModifier = optBed.map(b -> b.getXpMultiplierModifier(cropType)).orElse(0.0);
            CustomItemStatProvider itemStatsProvider = org.bukkit.Bukkit.getServicesManager().load(CustomItemStatProvider.class);
            SkyItemStats gearStats = (itemStatsProvider != null)
                    ? itemStatsProvider.getPlayerEquippedStats(player)
                    : SkyItemStats.EMPTY;
            PetStatProvider petStatsProvider = org.bukkit.Bukkit.getServicesManager().load(PetStatProvider.class);
            SkyItemStats petStats = (petStatsProvider != null)
                    ? petStatsProvider.getActivePetStats(player)
                    : SkyItemStats.EMPTY;
            HarvestRewardContext ctx = new HarvestRewardContext(
                    harvestId,
                    player.getUniqueId(),
                    garden,
                    plot,
                    cropType,
                    farmingLevel,
                    mastery,
                    baseDrops,
                    primaryBlockBreak,
                    surgeAoE,
                    bedDropModifier + gearStats.farmingFortune() + petStats.farmingFortune(),
                    bedXpModifier + gearStats.farmingXpBonus() + petStats.farmingXpBonus(),
                    ThreadLocalRandom.current().nextDouble(),
                    ThreadLocalRandom.current().nextDouble()
            );

            HarvestRewardPlan plan = rewardPipeline.calculatePlan(ctx);
            boolean committed = rewardPipeline.commitHarvest(player, block, garden, plot, plan, farmingLevel, sendFeedback);
            if (committed && petStatsProvider != null) {
                petStatsProvider.awardActivePetXp(player, 2.0);
            }
            return committed;
        } finally {
            rewardPipeline.releaseBlock(worldName, bx, by, bz, harvestId);
        }
    }

    private boolean hasHarvestPermission(@NotNull Player player, @NotNull Garden garden) {
        if (player.isOp() || player.hasPermission("skyrealms.garden.admin")) {
            return true;
        }
        Optional<Island> optIsland = islandService.getIsland(garden.islandId());
        return optIsland.isPresent() && optIsland.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK);
    }

    public static boolean isMature(@NotNull Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return switch (block.getType()) {
            case MELON, PUMPKIN, SUGAR_CANE, CACTUS -> true;
            default -> false;
        };
    }

    public static boolean isHoe(@NotNull Material mat) {
        return switch (mat) {
            case WOODEN_HOE, STONE_HOE, IRON_HOE, GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        if (!gardenService.isGardenWorld(event.getPlayer().getWorld())) {
            abilityService.deactivateSurgeWindowOnly(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // End active Surge window WITHOUT clearing cooldown so relogging cannot bypass cooldown
        abilityService.deactivateSurgeWindowOnly(uuid);
        journalService.flushAndEvict(uuid);
    }
}
