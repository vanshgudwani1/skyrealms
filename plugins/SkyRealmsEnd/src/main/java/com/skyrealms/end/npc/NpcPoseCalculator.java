package com.skyrealms.end.npc;

import com.skyrealms.end.config.EndVillageConfig.AnchorPoint;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pure deterministic kinematics & patrol interpolation calculator for vanilla-compatible
 * End Village NPC animations.
 */
public final class NpcPoseCalculator {

    public record PoseFrame(
            @NotNull EulerAngle head,
            @NotNull EulerAngle rightArm,
            @NotNull EulerAngle leftArm,
            boolean emitAccentEffect
    ) {}

    public record PatrolStep(double x, double y, double z, float yaw, int nextIndex) {}

    private NpcPoseCalculator() {}

    @NotNull
    public static PoseFrame computePose(
            @NotNull AnimationProfile profile,
            int phase,
            boolean playerNear,
            boolean interacting
    ) {
        double t = phase * 0.45;
        if (interacting) {
            return new PoseFrame(
                    new EulerAngle(0.12, 0.0, 0.0),
                    new EulerAngle(-0.95, 0.15, 0.25),
                    new EulerAngle(-0.35, -0.1, -0.1),
                    true
            );
        }

        return switch (profile) {
            case BLACKSMITH -> computeBlacksmith(phase, t);
            case MERCHANT -> computeMerchant(phase, t, playerNear);
            case MAGE -> computeMage(phase, t);
            case ARCHER -> computeArcher(phase, t);
            case WARRIOR -> computeWarrior(phase, t);
            case GUARD -> computeGuard(phase, t, playerNear);
            case QUEST -> computeQuest(phase, t, playerNear);
            case IDLE -> new PoseFrame(
                    new EulerAngle(playerNear ? 0.05 : Math.sin(t * 0.35) * 0.06, Math.sin(t * 0.25) * 0.12, 0.0),
                    new EulerAngle(-0.15 + Math.sin(t * 0.4) * 0.05, 0.0, 0.08),
                    new EulerAngle(-0.12 - Math.sin(t * 0.4) * 0.05, 0.0, -0.08),
                    false
            );
        };
    }

    private static PoseFrame computeBlacksmith(int phase, double t) {
        int cycle = phase % 16;
        if (cycle >= 11) {
            // Inspect forged weapon near chest/eye level
            return new PoseFrame(
                    new EulerAngle(0.28, Math.sin(t) * 0.08, 0.0),
                    new EulerAngle(-1.05, -0.25, 0.0),
                    new EulerAngle(-0.75, 0.35, 0.0),
                    false
            );
        }
        // Hammer strike downstroke on step 3 and step 7
        boolean strike = (cycle == 3 || cycle == 7);
        double armX = strike ? -0.35 : -1.55 + Math.sin(t * 1.4) * 0.35;
        return new PoseFrame(
                new EulerAngle(0.32, 0.0, 0.0),
                new EulerAngle(armX, 0.0, 0.05),
                new EulerAngle(-0.70, 0.30, -0.10),
                strike
        );
    }

    private static PoseFrame computeMerchant(int phase, double t, boolean playerNear) {
        int cycle = phase % 20;
        double wave = playerNear ? Math.sin(t * 1.1) * 0.35 : Math.sin(t * 0.5) * 0.12;
        return new PoseFrame(
                new EulerAngle(0.12 + Math.sin(t * 0.4) * 0.06, Math.sin(t * 0.3) * 0.15, 0.0),
                new EulerAngle(-0.65 + wave, -0.18, 0.12),
                new EulerAngle(-0.55 - wave * 0.5, 0.22, -0.08),
                cycle == 4 && playerNear
        );
    }

    private static PoseFrame computeMage(int phase, double t) {
        int cycle = phase % 18;
        boolean cast = cycle >= 12;
        double lift = cast ? -1.45 - Math.sin(t * 1.6) * 0.2 : -0.85 + Math.sin(t * 0.6) * 0.12;
        return new PoseFrame(
                new EulerAngle(cast ? -0.12 : 0.08, Math.sin(t * 0.4) * 0.1, 0.0),
                new EulerAngle(lift, 0.15, 0.18),
                new EulerAngle(cast ? -1.15 : -0.35, -0.22, -0.15),
                cycle == 13
        );
    }

    private static PoseFrame computeArcher(int phase, double t) {
        int cycle = phase % 20;
        if (cycle < 10) {
            // Bow inspection
            return new PoseFrame(
                    new EulerAngle(0.22, -0.12, 0.0),
                    new EulerAngle(-0.78, -0.30, 0.0),
                    new EulerAngle(-0.82, 0.32, 0.0),
                    false
            );
        }
        boolean release = (cycle == 17);
        return new PoseFrame(
                new EulerAngle(-0.04, 0.15, 0.0),
                new EulerAngle(-1.35, 0.25, 0.0),
                new EulerAngle(-1.45, -0.15, 0.0),
                release
        );
    }

    private static PoseFrame computeWarrior(int phase, double t) {
        int cycle = phase % 16;
        if (cycle >= 12) {
            // Blade inspection
            return new PoseFrame(
                    new EulerAngle(0.20, 0.0, 0.0),
                    new EulerAngle(-1.10, -0.30, 0.0),
                    new EulerAngle(-0.55, 0.25, 0.0),
                    false
            );
        }
        boolean slash = (cycle == 4 || cycle == 9);
        double slashAngle = slash ? -0.35 : -1.35 + Math.sin(t) * 0.2;
        return new PoseFrame(
                new EulerAngle(0.06, Math.sin(t * 0.5) * 0.1, 0.0),
                new EulerAngle(slashAngle, -0.15, 0.22),
                new EulerAngle(-0.75, 0.45, -0.10),
                slash
        );
    }

    private static PoseFrame computeGuard(int phase, double t, boolean playerNear) {
        double scanY = playerNear ? 0.0 : Math.sin(t * 0.35) * 0.45;
        return new PoseFrame(
                new EulerAngle(0.04, scanY, 0.0),
                new EulerAngle(-0.55 + Math.sin(t * 0.4) * 0.06, 0.0, 0.05),
                new EulerAngle(-0.65, 0.35, -0.05),
                (phase % 25 == 0)
        );
    }

    private static PoseFrame computeQuest(int phase, double t, boolean playerNear) {
        int cycle = phase % 18;
        boolean writing = cycle >= 6 && cycle < 14;
        return new PoseFrame(
                new EulerAngle(writing ? 0.38 : 0.22, Math.sin(t * 0.3) * 0.08, 0.0),
                new EulerAngle(writing ? -0.78 + Math.sin(t * 2.1) * 0.14 : -0.62, -0.22, 0.0),
                new EulerAngle(-0.88, 0.30, 0.0),
                playerNear && cycle == 2
        );
    }

    /**
     * Advances a patrolling NPC smoothly along its configured waypoints without Bukkit pathfinding.
     */
    @NotNull
    public static PatrolStep stepPatrol(
            @NotNull List<AnchorPoint> route,
            int targetIndex,
            double curX,
            double curY,
            double curZ,
            double stepDistance
    ) {
        if (route.isEmpty()) {
            return new PatrolStep(curX, curY, curZ, 0f, 0);
        }
        int safeIdx = Math.floorMod(targetIndex, route.size());
        AnchorPoint target = route.get(safeIdx);
        double dx = target.x() - curX;
        double dy = target.y() - curY;
        double dz = target.z() - curZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= stepDistance || dist < 1e-4) {
            int next = (safeIdx + 1) % route.size();
            return new PatrolStep(target.x(), target.y(), target.z(), target.yaw(), next);
        }

        double ratio = stepDistance / dist;
        double nx = curX + dx * ratio;
        double ny = curY + dy * ratio;
        double nz = curZ + dz * ratio;
        float yaw = computeLookYaw(dx, dz);
        return new PatrolStep(nx, ny, nz, yaw, safeIdx);
    }

    public static float computeLookYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}

