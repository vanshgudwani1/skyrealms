package com.skyrealms.islands.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpatialGridAllocatorTest {

    @Test
    @DisplayName("Constructor should reject invalid spacing")
    void shouldRejectInvalidSpacing() {
        assertThatThrownBy(() -> new SpatialGridAllocator(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpatialGridAllocator(-500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Index 0 should map to origin (0, 0)")
    void shouldMapZeroToOrigin() {
        SpatialGridAllocator allocator = new SpatialGridAllocator(500);
        SpatialGridAllocator.GridCoord origin = SpatialGridAllocator.indexToGrid(0);
        assertThat(origin.x()).isEqualTo(0);
        assertThat(origin.z()).isEqualTo(0);

        SpatialGridAllocator.GridCoord center = allocator.getIslandCenter(0);
        assertThat(center.x()).isEqualTo(0);
        assertThat(center.z()).isEqualTo(0);
    }

    @Test
    @DisplayName("First 10,000 indexes must have unique bijective grid coordinates")
    void shouldProduceUniqueGridCoordsForFirstTenThousandIslands() {
        Set<SpatialGridAllocator.GridCoord> visitedCoords = new HashSet<>();
        Set<Long> visitedKeys = new HashSet<>();

        SpatialGridAllocator allocator = new SpatialGridAllocator(500);

        for (int i = 0; i < 10_000; i++) {
            SpatialGridAllocator.GridCoord grid = SpatialGridAllocator.indexToGrid(i);
            assertThat(visitedCoords.add(grid))
                    .as("Index %d mapped to duplicate grid coordinate (%d, %d)", i, grid.x(), grid.z())
                    .isTrue();

            SpatialGridAllocator.GridCoord worldCenter = allocator.getIslandCenter(i);
            long key = SpatialGridAllocator.gridKey(worldCenter.x(), worldCenter.z());
            assertThat(visitedKeys.add(key))
                    .as("Duplicate spatial hash key for index %d", i)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Nearest grid anchor should reverse arbitrary world coordinates to correct center")
    void shouldFindNearestGridAnchor() {
        SpatialGridAllocator allocator = new SpatialGridAllocator(500);

        // Near (0, 0)
        SpatialGridAllocator.GridCoord anchorOrigin = allocator.getNearestGridAnchor(120, -140);
        assertThat(anchorOrigin.x()).isEqualTo(0);
        assertThat(anchorOrigin.z()).isEqualTo(0);

        // Near (500, 0)
        SpatialGridAllocator.GridCoord anchorEast = allocator.getNearestGridAnchor(480, 20);
        assertThat(anchorEast.x()).isEqualTo(500);
        assertThat(anchorEast.z()).isEqualTo(0);

        // Near (-500, 1000)
        SpatialGridAllocator.GridCoord anchorOther = allocator.getNearestGridAnchor(-520, 1010);
        assertThat(anchorOther.x()).isEqualTo(-500);
        assertThat(anchorOther.z()).isEqualTo(1000);
    }
}
