package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.mastery.CropType;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Saved island plot layout template persisted in sr_garden_saved_layouts.
 */
public record SavedPlotLayout(
        @NotNull UUID layoutId,
        @NotNull UUID islandId,
        @NotNull String layoutName,
        @NotNull PlotLayoutPreset presetBase,
        @NotNull IrrigationType irrigationType,
        @NotNull DecorationStyle decorationStyle,
        @NotNull String bedsSerialized,
        @NotNull Instant createdAt
) {
    public record BedTemplate(
            @NotNull BedType bedType,
            CropType targetCrop,
            int minLocalX,
            int minLocalZ,
            int maxLocalX,
            int maxLocalZ
    ) {}

    @NotNull
    public static String serializeBeds(@NotNull List<CropBed> beds) {
        if (beds.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < beds.size(); i++) {
            CropBed bed = beds.get(i);
            if (i > 0) sb.append(";");
            sb.append(bed.bedType().name()).append(",")
                    .append(bed.targetCrop() != null ? bed.targetCrop().name() : "NONE").append(",")
                    .append(bed.minLocalX()).append(",")
                    .append(bed.minLocalZ()).append(",")
                    .append(bed.maxLocalX()).append(",")
                    .append(bed.maxLocalZ());
        }
        return sb.toString();
    }

    @NotNull
    public static List<BedTemplate> deserializeBeds(@NotNull String serialized) {
        List<BedTemplate> list = new ArrayList<>();
        if (serialized == null || serialized.isBlank()) return list;
        for (String token : serialized.split(";")) {
            String[] parts = token.trim().split(",");
            if (parts.length != 6) continue;
            try {
                BedType type = BedType.fromName(parts[0]);
                CropType crop = "NONE".equalsIgnoreCase(parts[1]) ? null : CropType.fromCropId(parts[1]);
                int minX = Integer.parseInt(parts[2]);
                int minZ = Integer.parseInt(parts[3]);
                int maxX = Integer.parseInt(parts[4]);
                int maxZ = Integer.parseInt(parts[5]);
                list.add(new BedTemplate(type, crop, minX, minZ, maxX, maxZ));
            } catch (NumberFormatException ignored) {}
        }
        return list;
    }

    @NotNull
    public List<CropBed> instantiateBeds(@NotNull UUID targetIslandId, int targetPlotIndex) {
        Objects.requireNonNull(targetIslandId, "targetIslandId cannot be null");
        List<CropBed> result = new ArrayList<>();
        for (BedTemplate t : deserializeBeds(bedsSerialized)) {
            result.add(new CropBed(
                    UUID.randomUUID(),
                    targetIslandId,
                    targetPlotIndex,
                    t.bedType(),
                    t.targetCrop(),
                    t.minLocalX(),
                    t.minLocalZ(),
                    t.maxLocalX(),
                    t.maxLocalZ()
            ));
        }
        return result;
    }
}

