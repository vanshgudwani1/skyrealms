package com.skyrealms.islands.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IslandModelTest {

    @Test
    @DisplayName("Island roles and permission hierarchy should enforce correct access levels")
    void shouldEnforceCorrectPermissionHierarchy() {
        UUID owner = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();

        UUID islandId = UUID.randomUUID();
        IslandLocation loc = new IslandLocation("skyrealms_islands", 0, 100, 0);
        IslandSettings settings = IslandSettings.defaultSettings();

        Island island = new Island(
                islandId,
                owner,
                loc,
                100,
                0,
                "Test Island",
                IslandStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                settings,
                Collections.emptyList(),
                Collections.emptyList()
        );

        island.addMember(new IslandMember(islandId, officer, IslandRole.OFFICER, Instant.now()));
        island.addMember(new IslandMember(islandId, member, IslandRole.MEMBER, Instant.now()));

        // Role verification
        assertThat(island.getRole(owner)).isEqualTo(IslandRole.LEADER);
        assertThat(island.getRole(officer)).isEqualTo(IslandRole.OFFICER);
        assertThat(island.getRole(member)).isEqualTo(IslandRole.MEMBER);
        assertThat(island.getRole(visitor)).isEqualTo(IslandRole.VISITOR);

        // Leader permissions: ALL
        for (IslandPermission p : IslandPermission.values()) {
            assertThat(island.hasPermission(owner, p)).isTrue();
        }

        // Officer permissions: ALL except RESET_ISLAND
        assertThat(island.hasPermission(officer, IslandPermission.BLOCK_BREAK)).isTrue();
        assertThat(island.hasPermission(officer, IslandPermission.INVITE_MEMBERS)).isTrue();
        assertThat(island.hasPermission(officer, IslandPermission.RESET_ISLAND)).isFalse();

        // Member permissions: Building, containers, interactions only
        assertThat(island.hasPermission(member, IslandPermission.BLOCK_BREAK)).isTrue();
        assertThat(island.hasPermission(member, IslandPermission.BLOCK_PLACE)).isTrue();
        assertThat(island.hasPermission(member, IslandPermission.CONTAINER_ACCESS)).isTrue();
        assertThat(island.hasPermission(member, IslandPermission.INVITE_MEMBERS)).isFalse();
        assertThat(island.hasPermission(member, IslandPermission.MANAGE_SETTINGS)).isFalse();

        // Visitor default permissions: NO break, NO place, NO container access
        assertThat(island.hasPermission(visitor, IslandPermission.BLOCK_BREAK)).isFalse();
        assertThat(island.hasPermission(visitor, IslandPermission.BLOCK_PLACE)).isFalse();
        assertThat(island.hasPermission(visitor, IslandPermission.CONTAINER_ACCESS)).isFalse();

        // When settings change to allow visitor container access
        IslandSettings modifiedSettings = new IslandSettings(
                true, false, false, true, false, "{}"
        );
        island.setSettings(modifiedSettings);
        assertThat(island.hasPermission(visitor, IslandPermission.CONTAINER_ACCESS)).isTrue();
    }

    @Test
    @DisplayName("Island spatial bounds checking should accurately identify coordinate inclusion")
    void shouldCheckSpatialContainmentAccurately() {
        IslandLocation loc = new IslandLocation("skyrealms_islands", 500, 100, 500);
        int size = 100; // Half-size is 50 -> X: [450, 550], Z: [450, 550]

        // Inside
        assertThat(loc.contains("skyrealms_islands", 500, 500, size)).isTrue();
        assertThat(loc.contains("skyrealms_islands", 450, 450, size)).isTrue();
        assertThat(loc.contains("skyrealms_islands", 550, 550, size)).isTrue();

        // Outside boundary
        assertThat(loc.contains("skyrealms_islands", 449, 500, size)).isFalse();
        assertThat(loc.contains("skyrealms_islands", 551, 500, size)).isFalse();
        assertThat(loc.contains("skyrealms_islands", 500, 551, size)).isFalse();

        // Different world
        assertThat(loc.contains("world", 500, 500, size)).isFalse();
    }
}
