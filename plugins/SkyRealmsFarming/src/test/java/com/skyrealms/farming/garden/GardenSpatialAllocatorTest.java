package com.skyrealms.farming.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GardenSpatialAllocatorTest {

    @Test
    @DisplayName("Constructor should reject non-positive spacing or oversized regionSize")
    void shouldValidateConstructor() {
        assertThatThrownBy(() -> new GardenSpatialAllocator(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GardenSpatialAllocator(256, 512))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Index 0 should map to origin (0, 0)")
    void indexZeroShouldMapToOrigin() {
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(512, 256);
        GardenSpatialAllocator.GridCoord coord = allocator.getGardenCenter(0);
        assertThat(coord.x()).isZero();
        assertThat(coord.z()).isZero();

        GardenSpatialAllocator.RegionBounds bounds = allocator.getRegionBounds(0);
        assertThat(bounds.minX()).isEqualTo(-128);
        assertThat(bounds.maxX()).isEqualTo(128);
        assertThat(bounds.minZ()).isEqualTo(-128);
        assertThat(bounds.maxZ()).isEqualTo(128);
    }

    @Test
    @DisplayName("First 5,000 garden region indexes must produce unique non-overlapping coordinates")
    void first5000IndexesMustBeUnique() {
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(512, 256);
        Set<GardenSpatialAllocator.GridCoord> coords = new HashSet<>();

        for (int i = 0; i < 5000; i++) {
            GardenSpatialAllocator.GridCoord c = allocator.getGardenCenter(i);
            boolean added = coords.add(c);
            assertThat(added)
                    .withFailMessage("Index %d generated duplicate coordinate (%d, %d)", i, c.x(), c.z())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Garden regions must never overlap and maintain safety buffer")
    void regionsMustNeverOverlap() {
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(512, 256);

        // Check first 100 regions pair-wise
        for (int i = 0; i < 100; i++) {
            GardenSpatialAllocator.RegionBounds b1 = allocator.getRegionBounds(i);
            for (int j = i + 1; j < 100; j++) {
                GardenSpatialAllocator.RegionBounds b2 = allocator.getRegionBounds(j);

                boolean xOverlap = b1.minX() <= b2.maxX() && b1.maxX() >= b2.minX();
                boolean zOverlap = b1.minZ() <= b2.maxZ() && b1.maxZ() >= b2.minZ();

                assertThat(xOverlap && zOverlap)
                        .withFailMessage("Garden regions %d and %d overlap!", i, j)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("Nearest grid anchor should reverse arbitrary world coordinates to correct center")
    void nearestAnchorShouldReverseCoords() {
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(512, 256);

        // Anchor at (512, -512)
        GardenSpatialAllocator.GridCoord anchor = allocator.getNearestGridAnchor(550, -490);
        assertThat(anchor.x()).isEqualTo(512);
        assertThat(anchor.z()).isEqualTo(-512);

        // Anchor at (0, 0)
        GardenSpatialAllocator.GridCoord origin = allocator.getNearestGridAnchor(100, -80);
        assertThat(origin.x()).isZero();
        assertThat(origin.z()).isZero();
    }
}
