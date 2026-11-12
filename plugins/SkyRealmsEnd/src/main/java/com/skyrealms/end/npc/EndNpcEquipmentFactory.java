package com.skyrealms.end.npc;

import com.skyrealms.end.district.EndDistrict;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Generates cohesive, role-authentic vanilla armor and held tool loadouts for End Village NPCs.
 */
public final class EndNpcEquipmentFactory {

    private record Loadout(
            @NotNull Material helmet,
            @NotNull Color chestColor,
            @NotNull Color legsColor,
            @NotNull Color bootsColor,
            @Nullable Material chestOverride,
            @NotNull Material mainHand,
            @Nullable Material offHand
    ) {}

    private static final Color OBSIDIAN = Color.fromRGB(32, 22, 48);
    private static final Color PURPUR = Color.fromRGB(152, 92, 168);
    private static final Color VIOLET = Color.fromRGB(110, 48, 175);
    private static final Color CRIMSON_GOLD = Color.fromRGB(145, 42, 62);
    private static final Color EMERALD_HUNTER = Color.fromRGB(48, 118, 72);
    private static final Color END_GOLD = Color.fromRGB(224, 198, 122);
    private static final Color ASTRAL_CYAN = Color.fromRGB(64, 172, 184);

    private static final Map<NpcRole, Loadout> ROLE_LOADOUTS = Map.ofEntries(
            Map.entry(NpcRole.STEWARD, new Loadout(Material.GOLDEN_HELMET, PURPUR, OBSIDIAN, OBSIDIAN, null, Material.WRITTEN_BOOK, Material.END_CRYSTAL)),
            Map.entry(NpcRole.HERALD, new Loadout(Material.AMETHYST_CLUSTER, VIOLET, OBSIDIAN, PURPUR, null, Material.GOAT_HORN, Material.ENDER_PEARL)),
            Map.entry(NpcRole.GUIDE, new Loadout(Material.AMETHYST_BLOCK, ASTRAL_CYAN, OBSIDIAN, PURPUR, null, Material.COMPASS, Material.FILLED_MAP)),
            Map.entry(NpcRole.ARCHIVIST, new Loadout(Material.CHISELED_BOOKSHELF, VIOLET, OBSIDIAN, OBSIDIAN, null, Material.WRITABLE_BOOK, Material.SPYGLASS)),
            Map.entry(NpcRole.GUILDMASTER, new Loadout(Material.NETHERITE_HELMET, PURPUR, OBSIDIAN, OBSIDIAN, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_SWORD, Material.SHIELD)),
            Map.entry(NpcRole.BLACKSMITH, new Loadout(Material.IRON_HELMET, OBSIDIAN, CRIMSON_GOLD, OBSIDIAN, null, Material.NETHERITE_PICKAXE, Material.ANVIL)),
            Map.entry(NpcRole.TRAINER, new Loadout(Material.CHAINMAIL_HELMET, CRIMSON_GOLD, OBSIDIAN, OBSIDIAN, Material.IRON_CHESTPLATE, Material.IRON_SWORD, Material.SHIELD)),
            Map.entry(NpcRole.FLETCHER, new Loadout(Material.LEATHER_HELMET, EMERALD_HUNTER, OBSIDIAN, EMERALD_HUNTER, null, Material.BOW, Material.ARROW)),
            Map.entry(NpcRole.HUNTER, new Loadout(Material.TURTLE_HELMET, EMERALD_HUNTER, OBSIDIAN, OBSIDIAN, null, Material.CROSSBOW, Material.TIPPED_ARROW)),
            Map.entry(NpcRole.COLLECTOR, new Loadout(Material.CHISELED_POLISHED_BLACKSTONE, END_GOLD, OBSIDIAN, PURPUR, null, Material.SHULKER_SHELL, Material.AMETHYST_SHARD)),
            Map.entry(NpcRole.RESEARCHER, new Loadout(Material.TINTED_GLASS, VIOLET, OBSIDIAN, PURPUR, null, Material.BLAZE_ROD, Material.ENCHANTED_BOOK)),
            Map.entry(NpcRole.ALCHEMIST, new Loadout(Material.BREWING_STAND, PURPUR, VIOLET, OBSIDIAN, null, Material.DRAGON_BREATH, Material.SPLASH_POTION)),
            Map.entry(NpcRole.ENCHANTER, new Loadout(Material.AMETHYST_CLUSTER, VIOLET, PURPUR, OBSIDIAN, null, Material.ENCHANTED_BOOK, Material.LAPIS_LAZULI)),
            Map.entry(NpcRole.MERCHANT, new Loadout(Material.END_STONE_BRICKS, ASTRAL_CYAN, PURPUR, OBSIDIAN, null, Material.EMERALD, Material.BUNDLE)),
            Map.entry(NpcRole.AUCTION, new Loadout(Material.GOLD_BLOCK, END_GOLD, VIOLET, OBSIDIAN, null, Material.CLOCK, Material.GOLD_INGOT)),
            Map.entry(NpcRole.QUEST, new Loadout(Material.BOOKSHELF, END_GOLD, OBSIDIAN, PURPUR, null, Material.ENCHANTED_BOOK, Material.FILLED_MAP)),
            Map.entry(NpcRole.LICENSE, new Loadout(Material.DRAGON_HEAD, VIOLET, OBSIDIAN, PURPUR, Material.NETHERITE_CHESTPLATE, Material.ENDER_EYE, Material.END_CRYSTAL)),
            Map.entry(NpcRole.GUARD, new Loadout(Material.NETHERITE_HELMET, OBSIDIAN, OBSIDIAN, OBSIDIAN, Material.IRON_CHESTPLATE, Material.NETHERITE_SWORD, Material.SHIELD))
    );

    private EndNpcEquipmentFactory() {}

    public static void applyLoadout(@NotNull EntityEquipment equipment, @NotNull EndNpcDefinition npc) {
        Objects.requireNonNull(equipment, "equipment cannot be null");
        Objects.requireNonNull(npc, "npc cannot be null");

        Loadout loadout = ROLE_LOADOUTS.getOrDefault(npc.role(), ROLE_LOADOUTS.get(NpcRole.MERCHANT));

        // Guildmaster & Trainer overrides for Archer & Arcane districts so discipline identity stands out
        Material mainHand = loadout.mainHand();
        Material offHand = loadout.offHand();
        Material helmet = loadout.helmet();
        if (npc.district() == EndDistrict.ARCHER_QUARTER && (npc.role() == NpcRole.GUILDMASTER || npc.role() == NpcRole.TRAINER)) {
            mainHand = Material.BOW;
            offHand = Material.ARROW;
            helmet = Material.CHAINMAIL_HELMET;
        } else if (npc.district() == EndDistrict.ARCANE_QUARTER && (npc.role() == NpcRole.GUILDMASTER || npc.role() == NpcRole.TRAINER)) {
            mainHand = Material.BREEZE_ROD;
            offHand = Material.AMETHYST_SHARD;
            helmet = Material.AMETHYST_CLUSTER;
        }

        equipment.setHelmet(cleanItem(helmet));
        equipment.setChestplate(loadout.chestOverride() != null
                ? cleanItem(loadout.chestOverride())
                : dyedLeather(Material.LEATHER_CHESTPLATE, loadout.chestColor()));
        equipment.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, loadout.legsColor()));
        equipment.setBoots(dyedLeather(Material.LEATHER_BOOTS, loadout.bootsColor()));
        equipment.setItemInMainHand(cleanItem(mainHand));
        if (offHand != null) {
            equipment.setItemInOffHand(cleanItem(offHand));
        }
    }

    @NotNull
    private static ItemStack cleanItem(@NotNull Material mat) {
        ItemStack stack = new ItemStack(mat);
        if (stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @NotNull
    private static ItemStack dyedLeather(@NotNull Material mat, @NotNull Color color) {
        ItemStack stack = new ItemStack(mat);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

