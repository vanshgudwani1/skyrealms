package com.skyrealms.farming.model;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Root domain entity representing an active or stored farming generator structure.
 */
public final class FarmingGenerator {

    private final UUID generatorId;
    private final UUID islandId;
    private final UUID ownerUuid;
    private FarmingGeneratorTier tier;
    private final String worldName;
    private final int coreX;
    private final int coreY;
    private final int coreZ;
    private GeneratorStatus status;
    private long totalHarvests;
    private final Instant createdAt;
    private Instant updatedAt;

    private final Map<String, GeneratorUpgrade> upgrades = new ConcurrentHashMap<>();
    private final Set<CropNode> cropNodes = ConcurrentHashMap.newKeySet();

    public FarmingGenerator(
            @NotNull UUID generatorId,
            @NotNull UUID islandId,
            @NotNull UUID ownerUuid,
            @NotNull FarmingGeneratorTier tier,
            @NotNull String worldName,
            int coreX,
            int coreY,
            int coreZ,
            @NotNull GeneratorStatus status,
            long totalHarvests,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull Collection<GeneratorUpgrade> upgrades
    ) {
        this.generatorId = Objects.requireNonNull(generatorId, "generatorId cannot be null");
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
        this.tier = Objects.requireNonNull(tier, "tier cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.totalHarvests = totalHarvests;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        for (GeneratorUpgrade u : upgrades) {
            this.upgrades.put(u.key(), u);
        }
    }

    @NotNull
    public UUID generatorId() {
        return generatorId;
    }

    @NotNull
    public UUID islandId() {
        return islandId;
    }

    @NotNull
    public UUID ownerUuid() {
        return ownerUuid;
    }

    @NotNull
    public FarmingGeneratorTier tier() {
        return tier;
    }

    public void setTier(@NotNull FarmingGeneratorTier tier) {
        this.tier = Objects.requireNonNull(tier);
        this.updatedAt = Instant.now();
    }

    @NotNull
    public String worldName() {
        return worldName;
    }

    public int coreX() {
        return coreX;
    }

    public int coreY() {
        return coreY;
    }

    public int coreZ() {
        return coreZ;
    }

    @NotNull
    public GeneratorStatus status() {
        return status;
    }

    public void setStatus(@NotNull GeneratorStatus status) {
        this.status = Objects.requireNonNull(status);
        this.updatedAt = Instant.now();
    }

    public long totalHarvests() {
        return totalHarvests;
    }

    public void incrementHarvests() {
        this.totalHarvests++;
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Instant createdAt() {
        return createdAt;
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    @NotNull
    public Map<String, GeneratorUpgrade> upgrades() {
        return Collections.unmodifiableMap(upgrades);
    }

    public int getUpgradeLevel(@NotNull String key) {
        GeneratorUpgrade u = upgrades.get(key.toLowerCase());
        return u != null ? u.level() : 0;
    }

    public void applyUpgrade(@NotNull GeneratorUpgrade upgrade) {
        upgrades.put(upgrade.key(), upgrade);
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Set<CropNode> cropNodes() {
        return Collections.unmodifiableSet(cropNodes);
    }

    public void setCropNodes(@NotNull Collection<CropNode> nodes) {
        cropNodes.clear();
        cropNodes.addAll(nodes);
    }

    public void addCropNode(@NotNull CropNode node) {
        cropNodes.add(node);
    }

    @Nullable
    public CropNode findNodeAt(@NotNull Location location) {
        for (CropNode node : cropNodes) {
            if (node.matches(location)) {
                return node;
            }
        }
        return null;
    }

    public boolean isCoreAt(@NotNull Location location) {
        if (location.getWorld() == null) return false;
        return location.getWorld().getName().equals(worldName)
                && location.getBlockX() == coreX
                && location.getBlockY() == coreY
                && location.getBlockZ() == coreZ;
    }
}
