package com.skyrealms.pets.service;

import com.skyrealms.pets.catalog.PetCatalog;
import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.model.PlayerPetData;
import com.skyrealms.pets.repository.PetRepository;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authoritative service managing Cubees pet unlocks, summoning, XP/leveling,
 * active Garden stat bonuses, and persistent database storage.
 */
public final class PetService implements PetStatProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PetService.class);
    private static final NamespacedKey PET_SPEED_KEY = new NamespacedKey("skyrealms", "pet_walk_speed");
    private static final String CURRENCY_ID = "coins";

    private final Plugin plugin;
    private final PetCatalog catalog;
    private final PetRepository repository;
    private final PetEntityManager entityManager;
    private final ExecutorService dbExecutor;

    private final Map<UUID, Map<String, PlayerPetData>> cache = new ConcurrentHashMap<>();

    public PetService(
            @NotNull Plugin plugin,
            @NotNull PetCatalog catalog,
            @NotNull PetRepository repository,
            @NotNull PetEntityManager entityManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager cannot be null");
        this.dbExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SkyRealms-Pets-DB");
            t.setDaemon(true);
            return t;
        });
    }

    @NotNull
    public PetCatalog catalog() {
        return catalog;
    }

    @NotNull
    public PetEntityManager entityManager() {
        return entityManager;
    }

    public void handlePlayerJoin(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, PlayerPetData> loaded = repository.loadPlayerPets(uuid);
                if (!loaded.containsKey(PetCatalog.STARTER_PET_ID)) {
                    PlayerPetData starter = new PlayerPetData(
                            uuid, PetCatalog.STARTER_PET_ID, 1, 0.0, null, false, System.currentTimeMillis());
                    repository.savePet(starter);
                    loaded.put(PetCatalog.STARTER_PET_ID, starter);
                }
                return new ConcurrentHashMap<>(loaded);
            } catch (SQLException ex) {
                log.error("Failed loading pets for {}: {}", player.getName(), ex.getMessage(), ex);
                Map<String, PlayerPetData> fallback = new ConcurrentHashMap<>();
                fallback.put(PetCatalog.STARTER_PET_ID, new PlayerPetData(
                        uuid, PetCatalog.STARTER_PET_ID, 1, 0.0, null, false, System.currentTimeMillis()));
                return fallback;
            }
        }, dbExecutor).thenAccept(petsMap -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            cache.put(uuid, petsMap);
            getActivePetData(uuid).ifPresent(active -> {
                PetDefinition def = catalog.find(active.petId());
                if (def != null) {
                    entityManager.spawnPet(player, def, active);
                    syncPetSpeed(player);
                }
            });
        }));
    }

    public void handlePlayerQuit(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        entityManager.despawnPet(uuid);
        clearPetSpeed(player);
        cache.remove(uuid);
    }

    @NotNull
    public Map<String, PlayerPetData> getUnlockedPets(@NotNull UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, id -> {
            Map<String, PlayerPetData> map = new ConcurrentHashMap<>();
            map.put(PetCatalog.STARTER_PET_ID, new PlayerPetData(
                    id, PetCatalog.STARTER_PET_ID, 1, 0.0, null, false, System.currentTimeMillis()));
            return map;
        });
    }

    @NotNull
    public Optional<PlayerPetData> getActivePetData(@NotNull UUID playerUuid) {
        Map<String, PlayerPetData> map = cache.get(playerUuid);
        if (map == null) {
            return Optional.empty();
        }
        return map.values().stream().filter(PlayerPetData::active).findFirst();
    }

    @Override
    @NotNull
    public SkyItemStats getActivePetStats(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        Optional<PlayerPetData> activeOpt = getActivePetData(player.getUniqueId());
        if (activeOpt.isEmpty()) {
            return SkyItemStats.EMPTY;
        }
        PlayerPetData data = activeOpt.get();
        PetDefinition def = catalog.find(data.petId());
        return def != null ? def.statsForLevel(data.level()) : SkyItemStats.EMPTY;
    }

    @Override
    public void awardActivePetXp(@NotNull Player player, double xpAmount) {
        if (xpAmount <= 0.0) {
            return;
        }
        Optional<PlayerPetData> activeOpt = getActivePetData(player.getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }
        PlayerPetData data = activeOpt.get();
        if (data.level() >= PetDefinition.MAX_LEVEL) {
            return;
        }
        PetDefinition def = catalog.find(data.petId());
        if (def == null) {
            return;
        }

        boolean leveledUp = false;
        double xp = data.xp() + xpAmount;
        int level = data.level();
        while (level < PetDefinition.MAX_LEVEL) {
            double needed = PlayerPetData.xpRequiredForNextLevel(level);
            if (xp < needed) {
                break;
            }
            xp -= needed;
            level++;
            leveledUp = true;
        }
        if (level >= PetDefinition.MAX_LEVEL) {
            xp = 0.0;
        }
        data.setLevel(level);
        data.setXp(xp);

        if (leveledUp) {
            entityManager.updatePetName(player.getUniqueId(), def, data);
            syncPetSpeed(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            player.sendMessage(Component.text("✦ Your ", NamedTextColor.GOLD)
                    .append(Component.text(def.displayName(), def.color(), TextDecoration.BOLD))
                    .append(Component.text(" leveled up to Lv." + level + "!", NamedTextColor.GREEN)));
            persistAsync(data);
        } else if (Math.floor(xp) % 25 == 0) {
            persistAsync(data);
        }
    }

    public boolean unlockPetWithCoins(@NotNull Player player, @NotNull PetDefinition def) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerPetData> unlocked = getUnlockedPets(uuid);
        if (unlocked.containsKey(def.id())) {
            return true;
        }
        long cost = def.unlockCostCoins();
        if (cost > 0L) {
            EconomyProvider economy = Bukkit.getServicesManager().load(EconomyProvider.class);
            if (economy == null) {
                player.sendMessage(Component.text("✖ Economy service is unavailable.", NamedTextColor.RED));
                return false;
            }
            BigDecimal amount = BigDecimal.valueOf(cost);
            if (!economy.has(uuid, CURRENCY_ID, amount)) {
                player.sendMessage(Component.text(String.format("✖ You need %,d coins to unlock %s!", cost, def.displayName()), NamedTextColor.RED));
                return false;
            }
            economy.withdraw(uuid, CURRENCY_ID, amount, "Unlock Pet: " + def.id());
        }

        PlayerPetData newData = new PlayerPetData(uuid, def.id(), 1, 0.0, null, false, System.currentTimeMillis());
        unlocked.put(def.id(), newData);
        persistAsync(newData);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        player.sendMessage(Component.text("✦ Unlocked ", NamedTextColor.GREEN)
                .append(Component.text(def.displayName(), def.color(), TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GREEN)));
        return true;
    }

    public void grantPetFree(@NotNull Player target, @NotNull PetDefinition def) {
        UUID uuid = target.getUniqueId();
        Map<String, PlayerPetData> unlocked = getUnlockedPets(uuid);
        if (!unlocked.containsKey(def.id())) {
            PlayerPetData newData = new PlayerPetData(uuid, def.id(), 1, 0.0, null, false, System.currentTimeMillis());
            unlocked.put(def.id(), newData);
            persistAsync(newData);
        }
    }

    public boolean toggleSummon(@NotNull Player player, @NotNull PetDefinition def) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerPetData> unlocked = getUnlockedPets(uuid);
        PlayerPetData data = unlocked.get(def.id());
        if (data == null) {
            return false;
        }

        if (data.active()) {
            dismissActivePet(player, true);
            return true;
        }

        for (PlayerPetData other : unlocked.values()) {
            other.setActive(false);
        }
        data.setActive(true);
        if (entityManager.spawnPet(player, def, data)) {
            syncPetSpeed(player);
            persistActiveAsync(uuid, def.id());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            player.sendMessage(Component.text("✦ Summoned ", NamedTextColor.GREEN)
                    .append(Component.text(def.displayName() + " [Lv." + data.level() + "]", def.color(), TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            return true;
        } else {
            data.setActive(false);
            player.sendMessage(Component.text("✖ ModelEngine blueprint '" + def.blueprintId() + "' is not loaded yet.", NamedTextColor.RED));
            return false;
        }
    }

    public void dismissActivePet(@NotNull Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerPetData> unlocked = cache.get(uuid);
        if (unlocked != null) {
            unlocked.values().forEach(p -> p.setActive(false));
        }
        entityManager.despawnPet(uuid);
        clearPetSpeed(player);
        persistActiveAsync(uuid, null);
        if (notify) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            player.sendMessage(Component.text("✦ Pet recalled.", NamedTextColor.YELLOW));
        }
    }

    public boolean renameActivePet(@NotNull Player player, @Nullable String newName) {
        Optional<PlayerPetData> activeOpt = getActivePetData(player.getUniqueId());
        if (activeOpt.isEmpty()) {
            return false;
        }
        PlayerPetData data = activeOpt.get();
        PetDefinition def = catalog.find(data.petId());
        if (def == null) {
            return false;
        }
        String sanitized = (newName == null || newName.isBlank()) ? null : newName.trim();
        if (sanitized != null && sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        data.setNickname(sanitized);
        entityManager.updatePetName(player.getUniqueId(), def, data);
        persistAsync(data);
        return true;
    }

    public void syncPetSpeed(@NotNull Player player) {
        double speed = getActivePetStats(player).speedBonus();
        AttributeInstance instance = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(PET_SPEED_KEY);
        if (speed <= 0.0) {
            if (existing != null) {
                instance.removeModifier(PET_SPEED_KEY);
            }
            return;
        }
        if (existing != null) {
            if (Double.compare(existing.getAmount(), speed) == 0) {
                return;
            }
            instance.removeModifier(PET_SPEED_KEY);
        }
        instance.addTransientModifier(new AttributeModifier(PET_SPEED_KEY, speed, AttributeModifier.Operation.ADD_SCALAR));
    }

    public void clearPetSpeed(@NotNull Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (instance != null && instance.getModifier(PET_SPEED_KEY) != null) {
            instance.removeModifier(PET_SPEED_KEY);
        }
    }

    private void persistAsync(PlayerPetData data) {
        dbExecutor.submit(() -> {
            try {
                repository.savePet(data);
            } catch (SQLException ex) {
                log.warn("Failed persisting pet '{}' for {}: {}", data.petId(), data.playerUuid(), ex.getMessage());
            }
        });
    }

    private void persistActiveAsync(UUID playerUuid, @Nullable String activePetId) {
        dbExecutor.submit(() -> {
            try {
                repository.setActivePet(playerUuid, activePetId);
            } catch (SQLException ex) {
                log.warn("Failed updating active pet for {}: {}", playerUuid, ex.getMessage());
            }
        });
    }

    @Override
    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPetSpeed(player);
        }
        entityManager.stop();
        dbExecutor.shutdownNow();
        cache.clear();
    }
}

