package com.skyrealms.farming.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GardenPlotTest {

    @Test
    @DisplayName("Should reject plot indexes outside 1-16")
    void shouldRejectInvalidPlotIndex() {
        UUID islandId = UUID.randomUUID();
        assertThatThrownBy(() -> new GardenPlot(islandId, 0, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GardenPlot(islandId, 17, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Plots 1 to 8 belong to North row and 9 to 16 belong to South row")
    void shouldOrganizeIntoTwoRows() {
        UUID islandId = UUID.randomUUID();

        for (int i = 1; i <= 8; i++) {
            GardenPlot plot = new GardenPlot(islandId, i, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
            assertThat(plot.getRelativeCenterZ()).isEqualTo(-8);
        }

        for (int i = 9; i <= 16; i++) {
            GardenPlot plot = new GardenPlot(islandId, i, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
            assertThat(plot.getRelativeCenterZ()).isEqualTo(8);
        }
    }

    @Test
    @DisplayName("All 16 plots must have distinct bounding boxes without overlap")
    void all16PlotsMustNotOverlap() {
        UUID islandId = UUID.randomUUID();
        Set<String> positions = new HashSet<>();

        for (int i = 1; i <= 16; i++) {
            GardenPlot p1 = new GardenPlot(islandId, i, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
            String pos = p1.getRelativeCenterX() + "," + p1.getRelativeCenterZ();
            boolean added = positions.add(pos);
            assertThat(added)
                    .withFailMessage("Plot %d duplicates center %s", i, pos)
                    .isTrue();

            for (int j = i + 1; j <= 16; j++) {
                GardenPlot p2 = new GardenPlot(islandId, j, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
                boolean xOverlap = p1.getMinX(0) < p2.getMaxX(0) && p1.getMaxX(0) > p2.getMinX(0);
                boolean zOverlap = p1.getMinZ(0) < p2.getMaxZ(0) && p1.getMaxZ(0) > p2.getMinZ(0);

                assertThat(xOverlap && zOverlap)
                        .withFailMessage("Plot %d and %d overlap!", i, j)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("Contains should accurately identify coordinates inside plot footprint")
    void containsShouldMatchCoordinates() {
        UUID islandId = UUID.randomUUID();
        GardenPlot plot = new GardenPlot(islandId, 1, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);

        int cx = plot.getCenterX(0);
        int cz = plot.getCenterZ(0);

        assertThat(plot.contains(0, 0, cx, cz)).isTrue();
        assertThat(plot.contains(0, 0, cx + 2, cz - 2)).isTrue();
        assertThat(plot.contains(0, 0, cx + 10, cz)).isFalse();
        assertThat(plot.contains(0, 0, 0, 0)).isFalse();
    }
}
