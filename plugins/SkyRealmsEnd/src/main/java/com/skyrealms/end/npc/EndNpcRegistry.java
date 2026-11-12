package com.skyrealms.end.npc;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndDistrict;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory registry of all configured End Civilization NPCs indexed by ID, district, and action type.
 */
public final class EndNpcRegistry {

    private final Map<String, EndNpcDefinition> byId;
    private final Map<EndDistrict, List<EndNpcDefinition>> byDistrict;

    public EndNpcRegistry(@NotNull EndVillageConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        Map<String, EndNpcDefinition> map = new LinkedHashMap<>(config.npcs());
        this.byId = Collections.unmodifiableMap(map);

        Map<EndDistrict, List<EndNpcDefinition>> grouped = new EnumMap<>(EndDistrict.class);
        for (EndDistrict d : EndDistrict.values()) {
            grouped.put(d, map.values().stream().filter(n -> n.district() == d).toList());
        }
        this.byDistrict = Collections.unmodifiableMap(grouped);
    }

    @Nullable
    public EndNpcDefinition get(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<EndNpcDefinition> all() {
        return byId.values();
    }

    @NotNull
    public List<EndNpcDefinition> inDistrict(@NotNull EndDistrict district) {
        return byDistrict.getOrDefault(district, List.of());
    }

    public int size() {
        return byId.size();
    }
}

