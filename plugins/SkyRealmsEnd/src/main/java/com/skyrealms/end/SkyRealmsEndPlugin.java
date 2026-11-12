package com.skyrealms.end;

import com.skyrealms.end.module.EndModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Main Paper 1.21.1 entrypoint for SkyRealmsEnd.
 * Also supplies {@link FlatEndChunkGenerator} for the flat End testing world (`fullya_end`).
 */
public final class SkyRealmsEndPlugin extends JavaPlugin {

    private EndModule endModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.endModule = new EndModule(this);

        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(endModule);
            getSLF4JLogger().info("Registered 'end' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsEnd plugin loaded cleanly.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsEnd plugin disabled.");
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new FlatEndChunkGenerator();
    }

    public EndModule endModule() {
        return endModule;
    }

    /**
     * Generates a clean, infinite flat End Stone testing ground with surface walking height at Y=64.
     */
    private static final class FlatEndChunkGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(
                @NotNull WorldInfo worldInfo,
                @NotNull Random random,
                int chunkX,
                int chunkZ,
                @NotNull ChunkData chunkData
        ) {
            int minY = Math.max(0, worldInfo.getMinHeight());
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunkData.setBlock(x, minY, z, Material.BEDROCK);
                    for (int y = minY + 1; y <= 63; y++) {
                        chunkData.setBlock(x, y, z, Material.END_STONE);
                    }
                }
            }
        }

        @Override
        public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public @NotNull org.bukkit.block.Biome getBiome(@NotNull WorldInfo wi, int x, int y, int z) {
                    return org.bukkit.block.Biome.THE_END;
                }

                @Override
                public @NotNull List<org.bukkit.block.Biome> getBiomes(@NotNull WorldInfo wi) {
                    return List.of(org.bukkit.block.Biome.THE_END);
                }
            };
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}
