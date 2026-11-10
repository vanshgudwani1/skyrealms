package com.skyrealms.farming.garden.mastery;

import com.skyrealms.farming.garden.ability.GardenAbilitiesGui;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.ability.VerdantAuraManager;
import com.skyrealms.farming.garden.journal.BotanicalJournalGui;
import com.skyrealms.farming.garden.journal.BotanicalJournalService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Authoritative InventoryHolder click handler for Garden Abilities, Crop Mastery, and Botanical Journal GUIs.
 * Never trusts display names or lore for authorization—validates InventoryHolder and rawSlot exclusively.
 */
public final class CropMasteryGuiListener implements Listener {

    private final CropMasteryService masteryService;
    private final BotanicalJournalService journalService;
    private final McMMOFarmingService mcMmoService;
    private final GardenAbilityService abilityService;
    private final VerdantAuraManager auraManager;

    public CropMasteryGuiListener(
            @NotNull CropMasteryService masteryService,
            @NotNull BotanicalJournalService journalService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService,
            @NotNull VerdantAuraManager auraManager
    ) {
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.journalService = Objects.requireNonNull(journalService, "journalService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
        this.auraManager = Objects.requireNonNull(auraManager, "auraManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        if (holder instanceof CropMasteryGui.CropOverviewHolder) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (rawSlot == CropMasteryGui.SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (rawSlot == CropMasteryGui.SLOT_ABILITIES_HEADER || rawSlot == CropMasteryGui.SLOT_NAV_ABILITIES) {
                clickSound(player);
                GardenAbilitiesGui.open(player, mcMmoService, abilityService, auraManager);
                return;
            }
            if (rawSlot == CropMasteryGui.SLOT_NAV_JOURNAL) {
                clickSound(player);
                journalService.openJournalGui(player);
                return;
            }
            if (rawSlot == CropMasteryGui.SLOT_NAV_PLOTS) {
                clickSound(player);
                player.closeInventory();
                player.performCommand("garden plots");
                return;
            }

            CropType[] types = CropType.values();
            for (int i = 0; i < CropMasteryGui.CROP_SLOTS.length && i < types.length; i++) {
                if (rawSlot == CropMasteryGui.CROP_SLOTS[i]) {
                    clickSound(player);
                    masteryService.openCropDetailGui(player, types[i]);
                    return;
                }
            }
        } else if (holder instanceof CropMasteryGui.CropDetailHolder) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (rawSlot == CropMasteryGui.SLOT_CLOSE) {
                clickSound(player);
                masteryService.openMasteryGui(player);
            } else if (rawSlot == CropMasteryGui.SLOT_NAV_JOURNAL) {
                clickSound(player);
                journalService.openJournalGui(player);
            }
        } else if (holder instanceof GardenAbilitiesGui.AbilitiesHolder) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (rawSlot == GardenAbilitiesGui.SLOT_CLOSE) {
                player.closeInventory();
            } else if (rawSlot == GardenAbilitiesGui.SLOT_NAV_MASTERY) {
                clickSound(player);
                masteryService.openMasteryGui(player);
            } else if (rawSlot == GardenAbilitiesGui.SLOT_NAV_JOURNAL) {
                clickSound(player);
                journalService.openJournalGui(player);
            } else if (rawSlot == GardenAbilitiesGui.SLOT_NAV_PLOTS) {
                clickSound(player);
                player.closeInventory();
                player.performCommand("garden plots");
            }
        } else if (holder instanceof BotanicalJournalGui.JournalHolder) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (rawSlot == BotanicalJournalGui.SLOT_CLOSE) {
                player.closeInventory();
            } else if (rawSlot == BotanicalJournalGui.SLOT_NAV_MASTERY) {
                clickSound(player);
                masteryService.openMasteryGui(player);
            } else if (rawSlot == BotanicalJournalGui.SLOT_NAV_ABILITIES) {
                clickSound(player);
                GardenAbilitiesGui.open(player, mcMmoService, abilityService, auraManager);
            } else if (rawSlot == BotanicalJournalGui.SLOT_NAV_PLOTS) {
                clickSound(player);
                player.closeInventory();
                player.performCommand("garden plots");
            }
        }
    }

    private static void clickSound(@NotNull Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
