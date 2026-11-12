package com.skyrealms.core.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldCommandTest {

    @Test
    void validatesWorldFolderNamesAgainstTraversal() {
        assertThat(WorldCommand.isValidWorldName("fullya")).isTrue();
        assertThat(WorldCommand.isValidWorldName("fullya_end")).isTrue();
        assertThat(WorldCommand.isValidWorldName("skyrealms_garden")).isTrue();
        assertThat(WorldCommand.isValidWorldName("")).isFalse();
        assertThat(WorldCommand.isValidWorldName("../world")).isFalse();
        assertThat(WorldCommand.isValidWorldName("a/b")).isFalse();
        assertThat(WorldCommand.isValidWorldName("world name")).isFalse();
    }

    @Test
    void detectsEndVoidWorldsAndDisablesTerrainGeneration() {
        assertThat(WorldCommand.isEndWorldName("fullya_end")).isTrue();
        assertThat(WorldCommand.isEndWorldName("world_the_end")).isTrue();
        assertThat(WorldCommand.isEndWorldName("fullya")).isFalse();

        WorldCommand.VoidEndChunkGenerator gen = new WorldCommand.VoidEndChunkGenerator();
        assertThat(gen.shouldGenerateNoise()).isFalse();
        assertThat(gen.shouldGenerateSurface()).isFalse();
        assertThat(gen.shouldGenerateStructures()).isFalse();
        assertThat(gen.shouldGenerateMobs()).isFalse();
    }
}

