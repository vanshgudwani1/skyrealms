package com.skyrealms.pets.repository;

import com.skyrealms.pets.model.PlayerPetData;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC persistence for unlocked player Cubees pets in {@code sr_player_pets}.
 */
public final class PetRepository {

    private final DatabaseService databaseService;

    public PetRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    @NotNull
    public Map<String, PlayerPetData> loadPlayerPets(@NotNull UUID playerUuid) throws SQLException {
        Map<String, PlayerPetData> result = new LinkedHashMap<>();
        String sql = "SELECT pet_id, level, xp, nickname, active, unlocked_at FROM sr_player_pets WHERE player_uuid = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String petId = rs.getString("pet_id");
                    result.put(petId, new PlayerPetData(
                            playerUuid,
                            petId,
                            rs.getInt("level"),
                            rs.getDouble("xp"),
                            rs.getString("nickname"),
                            rs.getBoolean("active"),
                            rs.getLong("unlocked_at")
                    ));
                }
            }
        }
        return result;
    }

    public void savePet(@NotNull PlayerPetData data) throws SQLException {
        String updateSql = """
                UPDATE sr_player_pets
                SET level = ?, xp = ?, nickname = ?, active = ?
                WHERE player_uuid = ? AND pet_id = ?
                """;
        String insertSql = """
                INSERT INTO sr_player_pets (player_uuid, pet_id, level, xp, nickname, active, unlocked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = databaseService.getConnection()) {
            int updated;
            try (PreparedStatement up = conn.prepareStatement(updateSql)) {
                up.setInt(1, data.level());
                up.setDouble(2, data.xp());
                up.setString(3, data.nickname());
                up.setBoolean(4, data.active());
                up.setString(5, data.playerUuid().toString());
                up.setString(6, data.petId());
                updated = up.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setString(1, data.playerUuid().toString());
                    ins.setString(2, data.petId());
                    ins.setInt(3, data.level());
                    ins.setDouble(4, data.xp());
                    ins.setString(5, data.nickname());
                    ins.setBoolean(6, data.active());
                    ins.setLong(7, data.unlockedAt());
                    ins.executeUpdate();
                }
            }
        }
    }

    public void setActivePet(@NotNull UUID playerUuid, @Nullable String activePetId) throws SQLException {
        try (Connection conn = databaseService.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement clear = conn.prepareStatement(
                        "UPDATE sr_player_pets SET active = FALSE WHERE player_uuid = ?")) {
                    clear.setString(1, playerUuid.toString());
                    clear.executeUpdate();
                }
                if (activePetId != null) {
                    try (PreparedStatement set = conn.prepareStatement(
                            "UPDATE sr_player_pets SET active = TRUE WHERE player_uuid = ? AND pet_id = ?")) {
                        set.setString(1, playerUuid.toString());
                        set.setString(2, activePetId);
                        set.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}

