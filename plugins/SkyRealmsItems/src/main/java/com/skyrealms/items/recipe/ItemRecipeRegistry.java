package com.skyrealms.items.recipe;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.service.ItemRegistryService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registers shaped survival crafting recipe for the starter Sprout Harvester Hoe.
 */
public final class ItemRecipeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ItemRecipeRegistry.class);

    private final ItemRegistryService itemService;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public ItemRecipeRegistry(@NotNull ItemRegistryService itemService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
    }

    public void registerAll() {
        registerShaped(ItemCatalog.SPROUT_HOE,
                new String[]{"WW ", " S ", " S "},
                'W', Material.HAY_BLOCK,
                'S', Material.STICK);

        log.info("Registered {} custom SkyRealms hoe recipe(s).", registeredKeys.size());
    }

    public void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    private void registerShaped(String itemId, String[] shape, Object... ingredients) {
        ItemStack result = itemService.createCustomItem(itemId, 1).orElse(null);
        if (result == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey("skyrealms", "recipe_" + itemId);
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        for (int i = 0; i + 1 < ingredients.length; i += 2) {
            char symbol = (Character) ingredients[i];
            Material mat = (Material) ingredients[i + 1];
            recipe.setIngredient(symbol, mat);
        }

        if (Bukkit.addRecipe(recipe)) {
            registeredKeys.add(key);
        }
    }
}
