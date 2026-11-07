package com.skyrealms.islands.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Biome provider fixing all void chunks to a single stable biome (PLAINS).
 */
public final class VoidBiomeProvider extends BiomeProvider {

    private final Biome biome;

    public VoidBiomeProvider(@NotNull Biome biome) {
        this.biome = biome;
    }

    public VoidBiomeProvider() {
        this(Biome.CHERRY_GROVE);
    }

    @Override
    @NotNull
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return biome;
    }

    @Override
    @NotNull
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return List.of(biome);
    }
}
