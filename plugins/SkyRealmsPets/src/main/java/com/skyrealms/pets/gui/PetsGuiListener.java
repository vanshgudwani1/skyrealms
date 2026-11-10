package com.skyrealms.pets.gui;

import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.service.PetService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Click handler for the SkyRealms Cubees Pets GUI.
 */
public final class PetsGuiListener implements Listener {

    private final PetService petService;

    public PetsGuiListener(@NotNull PetService petService) {
        this.petService = Objects.requireNonNull(petService, "petService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PetsGui.PetsHolder)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == PetsGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (rawSlot == PetsGui.SLOT_DISMISS) {
            petService.dismissActivePet(player, true);
            PetsGui.open(player, petService);
            return;
        }

        List<PetDefinition> defs = List.copyOf(petService.catalog().all());
        for (int i = 0; i < PetsGui.PET_SLOTS.length && i < defs.size(); i++) {
            if (rawSlot == PetsGui.PET_SLOTS[i]) {
                PetDefinition def = defs.get(i);
                if (!petService.getUnlockedPets(player.getUniqueId()).containsKey(def.id())) {
                    if (petService.unlockPetWithCoins(player, def)) {
                        petService.toggleSummon(player, def);
                    }
                } else {
                    petService.toggleSummon(player, def);
                }
                PetsGui.open(player, petService);
                return;
            }
        }
    }
}

