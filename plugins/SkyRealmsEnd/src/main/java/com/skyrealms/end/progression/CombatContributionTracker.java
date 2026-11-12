package com.skyrealms.end.progression;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure, testable contribution-based XP attribution and anti-AFK / anti-duplication ledger.
 *
 * <p>Guarantees:</p>
 * <ul>
 *     <li>Single settlement per mob death via atomic {@code remove(mobUuid)} to prevent XP duplication.</li>
 *     <li>Proportional XP splitting across multiple players and multiple combat disciplines on the same mob.</li>
 *     <li>Stationary/auto-clicker AFK detection when repeated kills occur without meaningful position or camera movement.</li>
 * </ul>
 */
public final class CombatContributionTracker {

    public static final long LEDGER_EXPIRY_MILLIS = 300_000L; // 5 minutes
    public static final long AFK_WINDOW_MILLIS = 60_000L;     // 60s sliding window
    public static final int AFK_STATIONARY_KILL_LIMIT = 6;
    private static final double MIN_CONTRIBUTION_SHARE = 0.05;

    public record ContributorKey(@NotNull UUID playerUuid, @NotNull CombatDiscipline discipline) {}

    public record AttributedShare(
            @NotNull UUID playerUuid,
            @NotNull CombatDiscipline discipline,
            long xpAward,
            double contributionFraction
    ) {}

    public record AntiAfkVerdict(boolean allowed, double multiplier, @NotNull String reason) {
        public static final AntiAfkVerdict FULL = new AntiAfkVerdict(true, 1.0, "active");
    }

    private static final class MobLedger {
        final long createdAt;
        long lastUpdatedAt;
        final Map<ContributorKey, Double> damageByKey = new LinkedHashMap<>();

        MobLedger(long now) {
            this.createdAt = now;
            this.lastUpdatedAt = now;
        }
    }

    private record ActivitySample(double x, double y, double z, float yaw, float pitch, long timestamp) {}

    private final Map<UUID, MobLedger> mobLedgers = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<ActivitySample>> recentKillsByPlayer = new ConcurrentHashMap<>();

    public void recordDamage(
            @NotNull UUID mobUuid,
            @NotNull UUID playerUuid,
            @NotNull CombatDiscipline discipline,
            double damage,
            long nowMillis
    ) {
        Objects.requireNonNull(mobUuid, "mobUuid cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(discipline, "discipline cannot be null");
        if (damage <= 0.0 || !Double.isFinite(damage)) {
            return;
        }

        MobLedger ledger = mobLedgers.computeIfAbsent(mobUuid, ignored -> new MobLedger(nowMillis));
        synchronized (ledger) {
            ledger.lastUpdatedAt = nowMillis;
            ContributorKey key = new ContributorKey(playerUuid, discipline);
            ledger.damageByKey.merge(key, damage, Double::sum);
        }
    }

    /**
     * Atomically removes the damage ledger for {@code mobUuid} so an encounter can never pay out twice,
     * and computes proportional XP grants per {@code (player, discipline)} pair.
     */
    @NotNull
    public List<AttributedShare> consumeKillShares(
            @NotNull UUID mobUuid,
            long totalBaseXp,
            boolean eligibleKill
    ) {
        Objects.requireNonNull(mobUuid, "mobUuid cannot be null");
        MobLedger ledger = mobLedgers.remove(mobUuid);
        if (!eligibleKill || totalBaseXp <= 0L || ledger == null) {
            return List.of();
        }

        Map<ContributorKey, Double> copy;
        synchronized (ledger) {
            if (ledger.damageByKey.isEmpty()) {
                return List.of();
            }
            copy = new LinkedHashMap<>(ledger.damageByKey);
        }

        double totalDamage = 0.0;
        for (double dmg : copy.values()) {
            totalDamage += dmg;
        }
        if (totalDamage <= 0.0) {
            return List.of();
        }

        // Order descending by contribution so primary contributor receives any remainder first.
        List<Map.Entry<ContributorKey, Double>> ordered = new ArrayList<>(copy.entrySet());
        ordered.sort(Comparator.<Map.Entry<ContributorKey, Double>>comparingDouble(Map.Entry::getValue).reversed());

        List<AttributedShare> shares = new ArrayList<>(ordered.size());
        long remainingPool = totalBaseXp;
        for (Map.Entry<ContributorKey, Double> entry : ordered) {
            double fraction = entry.getValue() / totalDamage;
            if (fraction < MIN_CONTRIBUTION_SHARE && !ordered.isEmpty() && entry != ordered.get(0)) {
                continue;
            }
            long award = Math.min(remainingPool, Math.max(1L, Math.round(totalBaseXp * fraction)));
            if (award <= 0L) {
                continue;
            }
            remainingPool -= award;
            shares.add(new AttributedShare(
                    entry.getKey().playerUuid(),
                    entry.getKey().discipline(),
                    award,
                    fraction
            ));
        }
        return shares;
    }

    /**
     * Evaluates whether a player's kill activity shows real movement/aiming variance vs stationary AFK farming.
     */
    @NotNull
    public AntiAfkVerdict evaluateKillActivity(
            @NotNull UUID playerUuid,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long nowMillis
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Deque<ActivitySample> window = recentKillsByPlayer.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && (nowMillis - window.peekFirst().timestamp) > AFK_WINDOW_MILLIS) {
                window.pollFirst();
            }

            int stationaryCount = 0;
            for (ActivitySample prior : window) {
                double dx = x - prior.x;
                double dy = y - prior.y;
                double dz = z - prior.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                float yawDelta = Math.abs(normalizeAngleDelta(yaw - prior.yaw));
                float pitchDelta = Math.abs(pitch - prior.pitch);
                if (distSq < 1.25 && yawDelta < 6.0f && pitchDelta < 6.0f) {
                    stationaryCount++;
                }
            }

            window.addLast(new ActivitySample(x, y, z, yaw, pitch, nowMillis));
            if (stationaryCount >= AFK_STATIONARY_KILL_LIMIT) {
                return new AntiAfkVerdict(
                        false,
                        0.0,
                        "Anti-AFK guard triggered: move and aim between encounters to keep earning discipline XP."
                );
            }
            if (stationaryCount >= AFK_STATIONARY_KILL_LIMIT - 2) {
                return new AntiAfkVerdict(true, 0.5, "stationary diminishing returns (50%)");
            }
            return AntiAfkVerdict.FULL;
        }
    }

    public void forgetPlayer(@NotNull UUID playerUuid) {
        recentKillsByPlayer.remove(playerUuid);
    }

    public void purgeExpiredLedgers(long nowMillis) {
        mobLedgers.entrySet().removeIf(entry -> (nowMillis - entry.getValue().lastUpdatedAt) > LEDGER_EXPIRY_MILLIS);
    }

    public int trackedMobCount() {
        return mobLedgers.size();
    }

    private static float normalizeAngleDelta(float delta) {
        float wrapped = delta % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}

