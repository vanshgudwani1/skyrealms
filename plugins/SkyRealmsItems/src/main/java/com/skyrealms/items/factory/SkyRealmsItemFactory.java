package com.skyrealms.items.factory;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Creates, upgrades, and sanitizes SkyRealms custom {@link ItemStack} instances.
 * Uses Paper enchantment glint override so custom items carry zero vanilla enchantments,
 * while preserving external custom enchant data and lore (such as EliteEnchantments).
 */
public final class SkyRealmsItemFactory {

    public static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey("skyrealms", "custom_item_id");
    public static final NamespacedKey ITEM_RARITY_KEY = new NamespacedKey("skyrealms", "item_rarity");
    public static final NamespacedKey ITEM_DOMAIN_KEY = new NamespacedKey("skyrealms", "item_domain");
    public static final NamespacedKey ITEM_LEVEL_KEY = new NamespacedKey("skyrealms", "item_level");
    public static final NamespacedKey OWNED_LORE_KEY = new NamespacedKey("skyrealms", "owned_lore");

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ItemCatalog catalog;

    public SkyRealmsItemFactory(@NotNull ItemCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public boolean isCustomItem(@Nullable ItemStack stack) {
        return readCustomItemId(stack).isPresent();
    }

    @NotNull
    public Optional<String> readCustomItemId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String id = meta.getPersistentDataContainer().get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }

    @NotNull
    public Optional<CustomItemDefinition> readDefinition(@Nullable ItemStack stack) {
        return readCustomItemId(stack).map(catalog::find);
    }

    public int readItemLevel(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return HoeUpgradeService.MIN_LEVEL;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return HoeUpgradeService.MIN_LEVEL;
        }
        Integer stored = meta.getPersistentDataContainer().get(ITEM_LEVEL_KEY, PersistentDataType.INTEGER);
        return stored == null ? HoeUpgradeService.MIN_LEVEL : HoeUpgradeService.clampLevel(stored);
    }

    @NotNull
    public ItemStack createItem(@NotNull CustomItemDefinition def, int amount) {
        return createItem(def, amount, HoeUpgradeService.MIN_LEVEL);
    }

    @NotNull
    public ItemStack createItem(@NotNull CustomItemDefinition def, int amount, int level) {
        Objects.requireNonNull(def, "def cannot be null");
        ItemStack stack = new ItemStack(def.material(), Math.max(1, amount));
        applyItemState(stack, def, HoeUpgradeService.clampLevel(level), List.of(), List.of());
        return stack;
    }

    /**
     * Upgrades or evolves an existing hoe while preserving non-SkyRealms PDC/NBT data
     * and external lore lines (such as EliteEnchantments custom enchants and enchant slots).
     *
     * @return the upgraded {@link ItemStack} (possibly a new instance if material changed)
     */
    @NotNull
    public ItemStack upgradeItem(@NotNull ItemStack stack, @NotNull CustomItemDefinition newDef, int newLevel) {
        Objects.requireNonNull(stack, "stack cannot be null");
        Objects.requireNonNull(newDef, "newDef cannot be null");
        if (stack.getType() == Material.AIR) {
            return createItem(newDef, 1, newLevel);
        }

        int clampedLevel = HoeUpgradeService.clampLevel(newLevel);
        CustomItemDefinition previousDef = readDefinition(stack).orElse(newDef);
        int previousLevel = readItemLevel(stack);

        List<Component> prefixExternal = new ArrayList<>();
        List<Component> suffixExternal = new ArrayList<>();

        ItemMeta currentMeta = stack.getItemMeta();
        if (currentMeta != null && currentMeta.hasLore()) {
            splitExternalLore(currentMeta, previousDef, previousLevel, prefixExternal, suffixExternal);
        }

        ItemStack target = stack.getType() != newDef.material()
                ? stack.withType(newDef.material())
                : stack;
        applyItemState(target, newDef, clampedLevel, prefixExternal, suffixExternal);
        return target;
    }

    /**
     * Strips any Bukkit vanilla enchantments from a SkyRealms custom item without altering
     * custom PDC/NBT data or EliteEnchantments custom enchantments.
     *
     * @return true if one or more vanilla enchantments were removed
     */
    public boolean sanitizeVanillaEnchants(@Nullable ItemStack stack) {
        if (!isCustomItem(stack) || stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return false;
        }
        meta.removeEnchantments();
        Optional<CustomItemDefinition> def = readDefinition(stack);
        if (def.isPresent() && def.get().glint()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        stack.setItemMeta(meta);
        return true;
    }

    private void applyItemState(
            @NotNull ItemStack stack,
            @NotNull CustomItemDefinition def,
            int level,
            @NotNull List<Component> prefixExternal,
            @NotNull List<Component> suffixExternal
    ) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        // Guarantee zero vanilla enchantments on any created or upgraded custom item.
        if (meta.hasEnchants()) {
            meta.removeEnchantments();
        }

        meta.displayName(Component.text("✦ " + def.displayName() + " [Lv." + level + "]",
                        def.rarity().color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> skyLore = buildSkyRealmsLore(def, level);
        List<Component> combinedLore = new ArrayList<>(prefixExternal.size() + skyLore.size() + suffixExternal.size());
        combinedLore.addAll(prefixExternal);
        combinedLore.addAll(skyLore);
        combinedLore.addAll(suffixExternal);
        meta.lore(combinedLore);

        if (def.leatherColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(def.leatherColor());
        }
        if (def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }
        if (def.unbreakable()) {
            meta.setUnbreakable(true);
        }
        meta.setEnchantmentGlintOverride(def.glint() ? Boolean.TRUE : null);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, def.id());
        pdc.set(ITEM_RARITY_KEY, PersistentDataType.STRING, def.rarity().name());
        pdc.set(ITEM_DOMAIN_KEY, PersistentDataType.STRING, def.domain().name());
        pdc.set(ITEM_LEVEL_KEY, PersistentDataType.INTEGER, level);
        pdc.set(OWNED_LORE_KEY, PersistentDataType.STRING, encodeOwnedLore(skyLore));

        stack.setItemMeta(meta);
    }

    @NotNull
    public static List<Component> buildSkyRealmsLore(@NotNull CustomItemDefinition def, int level) {
        SkyItemStats scaled = HoeUpgradeService.scaleStats(def, level);
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text(def.domain().displayName() + " Hoe • Tier Level " + level + "/" + HoeUpgradeService.MAX_LEVEL_PER_TIER,
                        NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        appendStatsLore(lore, scaled);

        if (def.hasAbility()) {
            lore.add(Component.empty());
            lore.add(Component.text("Item Ability: " + def.abilityName() + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("RIGHT CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(def.abilityDescription(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (def.abilityCooldownSeconds() > 0) {
                lore.add(Component.text("Cooldown: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(def.abilityCooldownSeconds() + "s", NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (!def.loreLines().isEmpty()) {
            lore.add(Component.empty());
            for (String line : def.loreLines()) {
                lore.add(Component.text(line, NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Custom Enchants Allowed • Vanilla Enchants Blocked", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(def.rarity().label() + " HOE", def.rarity().color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private static void splitExternalLore(
            @NotNull ItemMeta meta,
            @NotNull CustomItemDefinition previousDef,
            int previousLevel,
            @NotNull List<Component> prefixExternal,
            @NotNull List<Component> suffixExternal
    ) {
        List<Component> existing = meta.lore();
        if (existing == null || existing.isEmpty()) {
            return;
        }

        Set<String> ownedNonBlank = new HashSet<>();
        String stored = meta.getPersistentDataContainer().get(OWNED_LORE_KEY, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            for (String line : stored.split("\n")) {
                if (!line.isBlank()) {
                    ownedNonBlank.add(line);
                }
            }
        } else {
            for (Component c : buildSkyRealmsLore(previousDef, previousLevel)) {
                String plain = PLAIN.serialize(c);
                if (!plain.isBlank()) {
                    ownedNonBlank.add(plain);
                }
            }
        }

        int firstOwned = -1;
        int lastOwned = -1;
        for (int i = 0; i < existing.size(); i++) {
            String plain = PLAIN.serialize(existing.get(i));
            if (!plain.isBlank() && ownedNonBlank.contains(plain)) {
                if (firstOwned == -1) {
                    firstOwned = i;
                }
                lastOwned = i;
            }
        }

        if (firstOwned == -1) {
            prefixExternal.addAll(existing);
            return;
        }

        for (int i = 0; i < firstOwned; i++) {
            prefixExternal.add(existing.get(i));
        }
        for (int i = firstOwned; i <= lastOwned; i++) {
            Component c = existing.get(i);
            String plain = PLAIN.serialize(c);
            if (!plain.isBlank() && !ownedNonBlank.contains(plain)) {
                suffixExternal.add(c);
            }
        }
        for (int i = lastOwned + 1; i < existing.size(); i++) {
            suffixExternal.add(existing.get(i));
        }
    }

    private static String encodeOwnedLore(List<Component> skyLore) {
        StringBuilder sb = new StringBuilder();
        for (Component line : skyLore) {
            String plain = PLAIN.serialize(line);
            if (!plain.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(plain);
            }
        }
        return sb.toString();
    }

    private static void appendStatsLore(List<Component> lore, SkyItemStats s) {
        addPercentStat(lore, "Farming Fortune", s.farmingFortune(), NamedTextColor.GREEN);
        addPercentStat(lore, "Farming XP", s.farmingXpBonus(), NamedTextColor.AQUA);
        addPercentStat(lore, "Walk Speed", s.speedBonus(), NamedTextColor.WHITE);
    }

    private static void addPercentStat(List<Component> lore, String label, double fraction, NamedTextColor color) {
        if (fraction <= 0.0) {
            return;
        }
        double pct = fraction * 100.0;
        String formatted = (Math.abs(pct - Math.rint(pct)) < 0.01)
                ? String.format("+%.0f%%", pct)
                : String.format("+%.1f%%", pct);
        lore.add(Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(formatted, color))
                .decoration(TextDecoration.ITALIC, false));
    }
}
