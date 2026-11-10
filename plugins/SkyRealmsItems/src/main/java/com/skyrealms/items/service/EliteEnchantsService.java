package com.skyrealms.items.service;

import com.skyrealms.platform.integrations.CustomEnchantProvider;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Integrates with EliteEnchantments to detect custom enchantments (telekinesis, replenish)
 * and execute direct-to-inventory drop delivery and seed-safe auto-replanting.
 */
public final class EliteEnchantsService implements CustomEnchantProvider {

    private static final Logger log = LoggerFactory.getLogger(EliteEnchantsService.class);

    private final BiPredicate<ItemStack, String> enchantChecker;

    public EliteEnchantsService() {
        this(EliteEnchantsService::queryEliteEnchantments);
    }

    public EliteEnchantsService(@NotNull BiPredicate<ItemStack, String> enchantChecker) {
        this.enchantChecker = Objects.requireNonNull(enchantChecker, "enchantChecker cannot be null");
    }

    @Override
    public boolean hasCustomEnchant(@Nullable ItemStack item, @NotNull String enchantId) {
        if (item == null || item.getType() == Material.AIR || enchantId.isBlank()) {
            return false;
        }
        return enchantChecker.test(item, enchantId);
    }

    @Override
    public void deliverOrDrop(
            @NotNull Player player,
            @NotNull Location fallbackDropLoc,
            @NotNull Collection<ItemStack> items,
            boolean directToInventory
    ) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(fallbackDropLoc, "fallbackDropLoc cannot be null");
        if (items.isEmpty() || fallbackDropLoc.getWorld() == null) {
            return;
        }

        for (ItemStack raw : items) {
            if (raw == null || raw.getType() == Material.AIR || raw.getAmount() <= 0) {
                continue;
            }
            ItemStack item = raw.clone();
            if (!directToInventory) {
                fallbackDropLoc.getWorld().dropItemNaturally(fallbackDropLoc, item);
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                if (leftover != null && leftover.getAmount() > 0) {
                    fallbackDropLoc.getWorld().dropItemNaturally(fallbackDropLoc, leftover);
                }
            }
        }
    }

    @Override
    public void handleAoEReplenish(
            @NotNull Player player,
            @NotNull ItemStack tool,
            @NotNull Block block,
            @NotNull BlockData harvestedData,
            @NotNull List<ItemStack> mutableDrops
    ) {
        if (!hasReplenish(tool)) {
            return;
        }
        Material seedMat = seedMaterialFor(harvestedData.getMaterial());
        if (seedMat == null) {
            return;
        }
        if (block.getType() != Material.AIR || !hasValidSupportForReplant(block, harvestedData)) {
            return;
        }
        if (consumeOneSeed(player, seedMat, mutableDrops)) {
            block.setBlockData(createReplantedData(harvestedData), true);
        }
    }

    /**
     * Returns true if {@code block} is an immature crop/seed, melon/pumpkin stem,
     * or root block of sugar cane / cactus that Replenish should protect from breaking.
     */
    public static boolean isProtectedSeedOrRoot(@NotNull Block block) {
        Material type = block.getType();
        if (type == Material.MELON_STEM
                || type == Material.PUMPKIN_STEM
                || type == Material.ATTACHED_MELON_STEM
                || type == Material.ATTACHED_PUMPKIN_STEM) {
            return true;
        }
        if (type == Material.SUGAR_CANE || type == Material.CACTUS) {
            return block.getRelative(BlockFace.DOWN).getType() != type;
        }
        if (isSupportedSeedCrop(type) && block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() < ageable.getMaximumAge();
        }
        if ((type == Material.TORCHFLOWER_CROP || type == Material.PITCHER_CROP || type == Material.SWEET_BERRY_BUSH)
                && block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() < ageable.getMaximumAge();
        }
        return false;
    }

    public static boolean isSupportedSeedCrop(@NotNull Material material) {
        return seedMaterialFor(material) != null;
    }

    @Nullable
    public static Material seedMaterialFor(@NotNull Material cropBlockMaterial) {
        return switch (cropBlockMaterial) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    @NotNull
    public static BlockData createReplantedData(@NotNull BlockData harvestedData) {
        BlockData fresh = harvestedData.getMaterial().createBlockData();
        if (fresh instanceof Ageable ageable) {
            ageable.setAge(0);
        }
        if (fresh instanceof Directional freshDir && harvestedData instanceof Directional oldDir) {
            freshDir.setFacing(oldDir.getFacing());
        }
        return fresh;
    }

    public static boolean hasValidSupportForReplant(@NotNull Block block, @NotNull BlockData harvestedData) {
        Material cropMat = harvestedData.getMaterial();
        return switch (cropMat) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS ->
                    block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND;
            case NETHER_WART ->
                    block.getRelative(BlockFace.DOWN).getType() == Material.SOUL_SAND;
            case COCOA -> {
                if (harvestedData instanceof Directional dir) {
                    Material behind = block.getRelative(dir.getFacing()).getType();
                    yield behind.name().contains("JUNGLE_LOG") || behind.name().contains("JUNGLE_WOOD");
                }
                yield false;
            }
            default -> false;
        };
    }

    public static boolean consumeOneSeed(
            @NotNull Player player,
            @NotNull Material seedMat,
            @Nullable List<ItemStack> dropStacks
    ) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (dropStacks != null) {
            Iterator<ItemStack> it = dropStacks.iterator();
            while (it.hasNext()) {
                ItemStack stack = it.next();
                if (stack != null && stack.getType() == seedMat && stack.getAmount() > 0) {
                    if (stack.getAmount() == 1) {
                        it.remove();
                    } else {
                        stack.setAmount(stack.getAmount() - 1);
                    }
                    return true;
                }
            }
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack invItem = contents[i];
            if (invItem != null && invItem.getType() == seedMat && invItem.getAmount() > 0) {
                if (invItem.getAmount() == 1) {
                    player.getInventory().setItem(i, null);
                } else {
                    invItem.setAmount(invItem.getAmount() - 1);
                }
                return true;
            }
        }
        return false;
    }

    public static void playReplantSound(@NotNull Block block, @NotNull Material cropMaterial) {
        if (block.getWorld() == null) {
            return;
        }
        Sound sound = cropMaterial == Material.NETHER_WART ? Sound.ITEM_NETHER_WART_PLANT : Sound.ITEM_CROP_PLANT;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.3, 0.5), sound, 0.45f, 1.15f);
    }

    // Cached reflection handles for EliteEnchantments EnchantManager -> EnchantController#hasEnchant
    private static volatile Object enchantControllerInstance;
    private static volatile Method hasEnchantMethod;
    private static volatile boolean loggedHookError;

    private static boolean queryEliteEnchantments(@NotNull ItemStack item, @NotNull String enchantId) {
        try {
            if (enchantControllerInstance == null || hasEnchantMethod == null) {
                initReflection();
            }
            Object controller = enchantControllerInstance;
            Method method = hasEnchantMethod;
            if (controller == null || method == null) {
                return false;
            }
            Object res = method.invoke(controller, item, enchantId);
            return Boolean.TRUE.equals(res);
        } catch (Throwable ex) {
            if (!loggedHookError) {
                loggedHookError = true;
                log.warn("Failed querying EliteEnchantments for enchant '{}': {}", enchantId, ex.getMessage());
            }
            return false;
        }
    }

    private static synchronized void initReflection() {
        if (enchantControllerInstance != null && hasEnchantMethod != null) {
            return;
        }
        try {
            Plugin ee = Bukkit.getPluginManager().getPlugin("EliteEnchantments");
            if (ee == null || !ee.isEnabled()) {
                return;
            }
            ClassLoader eeLoader = ee.getClass().getClassLoader();
            Class<?> managerClass = Class.forName(
                    "net.splodgebox.eliteenchantments.enchants.EnchantManager",
                    true,
                    eeLoader
            );
            Method getInstance = managerClass.getMethod("getInstance");
            Object manager = getInstance.invoke(null);
            if (manager == null) {
                return;
            }
            Method getController = managerClass.getMethod("getEnchantController");
            Object controller = getController.invoke(manager);
            if (controller == null) {
                return;
            }
            hasEnchantMethod = controller.getClass().getMethod("hasEnchant", ItemStack.class, String.class);
            enchantControllerInstance = controller;
            log.info("Hooked into EliteEnchantments EnchantController successfully.");
        } catch (Throwable ex) {
            if (!loggedHookError) {
                loggedHookError = true;
                log.warn("Could not hook into EliteEnchantments EnchantController: {}", ex.getMessage());
            }
        }
    }
}

