package com.skyrealms.farming.garden;

import com.skyrealms.farming.event.GardenCropHarvestEvent;
import com.skyrealms.farming.event.GardenCropPlantEvent;
import com.skyrealms.farming.model.FarmingPlayerProgress;
import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core gameplay engine managing natural crop planting, growth stages,
 * maturity validation, anti-exploit placed-block tracking, and right-click fast harvest.
 */
public final class NaturalFarmingEngine {

    private final Plugin plugin;
    private final GardenService gardenService;
    private final FarmingProgressionService progressionService;
    private final EventBus eventBus;

    // Anti-exploit: tracks block keys placed manually by players to prevent placed-crop XP farming
    private final Set<Long> playerPlacedBlocks = ConcurrentHashMap.newKeySet();

    // Configured XP per crop
    private final Map<String, Long> cropXpValues = new HashMap<>();

    public NaturalFarmingEngine(
            @NotNull Plugin plugin,
            @NotNull GardenService gardenService,
            @NotNull FarmingProgressionService progressionService,
            @NotNull EventBus eventBus
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");

        loadConfigValues();
    }

    public void loadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        cropXpValues.put("WHEAT", config.getLong("garden.crops.wheat.xp", 10L));
        cropXpValues.put("CARROTS", config.getLong("garden.crops.carrots.xp", 12L));
        cropXpValues.put("POTATOES", config.getLong("garden.crops.potatoes.xp", 12L));
        cropXpValues.put("BEETROOTS", config.getLong("garden.crops.beetroots.xp", 15L));
        cropXpValues.put("MELON", config.getLong("garden.crops.melon.xp", 18L));
        cropXpValues.put("PUMPKIN", config.getLong("garden.crops.pumpkin.xp", 20L));
        cropXpValues.put("SUGAR_CANE", config.getLong("garden.crops.sugar_cane.xp", 14L));
        cropXpValues.put("CACTUS", config.getLong("garden.crops.cactus.xp", 16L));
        cropXpValues.put("COCOA", config.getLong("garden.crops.cocoa.xp", 15L));
        cropXpValues.put("NETHER_WART", config.getLong("garden.crops.nether_wart.xp", 25L));
    }

    public long blockKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
    }

    public void markPlayerPlaced(@NotNull Block block) {
        playerPlacedBlocks.add(blockKey(block.getX(), block.getY(), block.getZ()));
    }

    public void clearPlayerPlaced(@NotNull Block block) {
        playerPlacedBlocks.remove(blockKey(block.getX(), block.getY(), block.getZ()));
    }

    public boolean isPlayerPlaced(@NotNull Block block) {
        return playerPlacedBlocks.contains(blockKey(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Determines if the given material is a supported SkyRealms garden crop.
     */
    public boolean isCropMaterial(@NotNull Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, MELON, PUMPKIN, SUGAR_CANE, CACTUS, COCOA, NETHER_WART -> true;
            default -> false;
        };
    }

    /**
     * Identifies canonical crop ID string for collections and progression.
     */
    @NotNull
    public String getCropId(@NotNull Material material) {
        return switch (material) {
            case WHEAT -> "WHEAT";
            case CARROTS -> "CARROTS";
            case POTATOES -> "POTATOES";
            case BEETROOTS -> "BEETROOTS";
            case MELON -> "MELON";
            case PUMPKIN -> "PUMPKIN";
            case SUGAR_CANE -> "SUGAR_CANE";
            case CACTUS -> "CACTUS";
            case COCOA -> "COCOA";
            case NETHER_WART -> "NETHER_WART";
            default -> material.name();
        };
    }

    /**
     * Verifies if a crop block is fully mature and eligible for harvest rewards.
     */
    public boolean isMature(@NotNull Block block) {
        Material mat = block.getType();
        BlockData data = block.getBlockData();

        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }

        if (mat == Material.MELON || mat == Material.PUMPKIN) {
            return !isPlayerPlaced(block);
        }

        if (mat == Material.SUGAR_CANE || mat == Material.CACTUS) {
            // Must be above another cane/cactus block (vertical growth)
            Block below = block.getRelative(0, -1, 0);
            return below.getType() == mat && !isPlayerPlaced(block);
        }

        return false;
    }

    @Nullable
    public Material getReplantCrop(@NotNull Material matureMaterial) {
        return switch (matureMaterial) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROTS;
            case POTATOES -> Material.POTATOES;
            case BEETROOTS -> Material.BEETROOTS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA;
            default -> null; // Stem crops (melon/pumpkin) and vertical crops do not replant the fruit block
        };
    }

    /**
     * Handles right-click fast harvest on a mature crop.
     * Replants automatically and deposits drops directly into player inventory.
     */
    public boolean handleRightClickHarvest(@NotNull Player player, @NotNull Block block) {
        if (!isMature(block)) {
            return false;
        }

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        if (optGarden.isEmpty()) return false;

        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optPlot.isEmpty() || !optPlot.get().unlocked()) return false;

        Garden garden = optGarden.get();
        GardenPlot plot = optPlot.get();
        Material originalMat = block.getType();
        String cropId = getCropId(originalMat);

        // Calculate drops
        List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        if (drops.isEmpty()) {
            drops.add(new ItemStack(originalMat));
        }

        long xp = cropXpValues.getOrDefault(cropId, 10L);

        // Fire event
        GardenCropHarvestEvent event = new GardenCropHarvestEvent(player, garden, plot, cropId, drops, xp);
        eventBus.publish(event);
        if (event.isCancelled()) {
            return false;
        }

        // Replant or reset age
        Material replantMat = getReplantCrop(originalMat);
        if (replantMat != null) {
            block.setType(replantMat, false);
            if (block.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(0);
                block.setBlockData(ageable, false);
            }
        } else {
            block.setType(Material.AIR, false);
        }
        clearPlayerPlaced(block);

        // Deliver drops to player
        for (ItemStack drop : drops) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
            if (!overflow.isEmpty()) {
                for (ItemStack remaining : overflow.values()) {
                    player.getWorld().dropItemNaturally(block.getLocation(), remaining);
                }
            }
        }

        // Award XP & Progress
        progressionService.awardXp(player, event.farmingXpAwarded());
        long totalCollected = drops.stream().filter(d -> d.getType() == originalMat).mapToInt(ItemStack::getAmount).sum();
        if (totalCollected == 0) totalCollected = 1;
        progressionService.incrementCollection(player.getUniqueId(), cropId, totalCollected);

        // Feedback particles & sound
        Location center = block.getLocation().clone().add(0.5, 0.5, 0.5);
        player.playSound(center, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, center, 6, 0.2, 0.2, 0.2, 0.05);

        FarmingPlayerProgress progress = progressionService.getProgress(player.getUniqueId());
        player.sendActionBar(Component.text("+ " + event.farmingXpAwarded() + " Farming XP ", TextColor.fromHexString("#77DD77"))
                .append(Component.text("[" + cropId + "] ", NamedTextColor.GOLD))
                .append(Component.text("(Level " + progress.farmingLevel() + ")", NamedTextColor.YELLOW)));

        return true;
    }

    /**
     * Handles normal BlockBreakEvent harvest rewards.
     */
    public boolean handleBreakHarvest(@NotNull Player player, @NotNull Block block) {
        if (!isMature(block)) {
            clearPlayerPlaced(block);
            return false;
        }

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        if (optGarden.isEmpty()) return false;

        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optPlot.isEmpty() || !optPlot.get().unlocked()) return false;

        Garden garden = optGarden.get();
        GardenPlot plot = optPlot.get();
        Material originalMat = block.getType();
        String cropId = getCropId(originalMat);

        long xp = cropXpValues.getOrDefault(cropId, 10L);
        List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));

        // Fire event
        GardenCropHarvestEvent event = new GardenCropHarvestEvent(player, garden, plot, cropId, drops, xp);
        eventBus.publish(event);
        if (event.isCancelled()) {
            return false;
        }

        clearPlayerPlaced(block);

        // Award XP & Progress
        progressionService.awardXp(player, event.farmingXpAwarded());
        long totalCollected = drops.stream().filter(d -> d.getType() == originalMat).mapToInt(ItemStack::getAmount).sum();
        if (totalCollected == 0) totalCollected = 1;
        progressionService.incrementCollection(player.getUniqueId(), cropId, totalCollected);

        FarmingPlayerProgress progress = progressionService.getProgress(player.getUniqueId());
        player.sendActionBar(Component.text("+ " + event.farmingXpAwarded() + " Farming XP ", TextColor.fromHexString("#77DD77"))
                .append(Component.text("[" + cropId + "] ", NamedTextColor.GOLD))
                .append(Component.text("(Level " + progress.farmingLevel() + ")", NamedTextColor.YELLOW)));

        return true;
    }
}
