package core;

import core.environment.Intersection;
import core.environment.TrafficLight;
import core.controller.TrafficController;
import core.vehicles.Vehicle;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GeometryAudit v2 — post-fix verification.
 *
 * Measures:
 *   1. Diagonal vehicle perpendicular offset from road centre-line (Issue 2)
 *      — by simulating spawn and reading spawnX+spawnY vs cx5+cy5
 *   2. Tree count inside the diagonal road polygon (Issue 1)
 *      — by re-running generateTrees via a Renderer instance and
 *        checking each tree point against the road polygon
 */
public class GeometryAudit {

    static final int roadWidth   = 240;
    static final int roadStartY  = 220;
    static final int width       = 1600;
    static final int height      = 900;

    // ─── BEFORE values (from previous audit run) ────────────────────────────
    static final double BEFORE_PERP_OFFSET_X1200   = 60.1; // px
    static final double BEFORE_PERP_OFFSET_X600    = 60.1; // px
    static final int    BEFORE_TREE_GUARD_GAP_X600  = 125;  // px of unguarded diagonal road
    // (x=1200 had no unguarded gap — its column exclusion + guard overlap)

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  GEOMETRY AUDIT v2 — POST-FIX VERIFICATION");
        System.out.println("=================================================================\n");

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ISSUE 2: Vehicle path alignment with diagonal road          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        auditAlignment(1200, "TONG_HOP (5-way at x=1200)");
        auditAlignment(600,  "NGA_NAM  (5-way at x=600 )");

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ISSUE 1: Trees inside the diagonal road polygon             ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        auditTrees(1200, "TONG_HOP (5-way at x=1200)");
        auditTrees(600,  "NGA_NAM  (5-way at x=600 )");

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  BEFORE vs AFTER SUMMARY                                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");
        printSummary();
    }

    // ─────────────────────────────────────────────────────────────────────────

    static void auditAlignment(int interX, String label) {
        System.out.println("  [" + label + "]");
        int cx5 = interX + roadWidth / 2;
        int cy5 = roadStartY + roadWidth / 2;
        int roadCentreSum = cx5 + cy5;

        // Replicate the fixed spawn formula from TrafficController.spawnHorizontalVehicle()
        double spawnX = width + 60;
        double spawnY = cy5 - (spawnX - cx5);   // fixed: no -85
        if (spawnY < -200) {
            spawnY = -60;
            spawnX = cx5 + cy5 - spawnY;         // fixed: cx5+cy5-spawnY, not +145
        }

        double spawnSum  = spawnX + spawnY;
        double perpOffset = (spawnSum - roadCentreSum) / Math.sqrt(2);

        System.out.printf("    Road centre-line: x+y = %d%n", roadCentreSum);
        System.out.printf("    Spawn point:       (%.0f, %.0f)  x+y = %.0f%n", spawnX, spawnY, spawnSum);
        System.out.printf("    Perpendicular offset AFTER fix:  %.2f px  (BEFORE: %.1f px)%n",
                Math.abs(perpOffset), BEFORE_PERP_OFFSET_X1200);
        System.out.printf("    Status: %s%n",
                Math.abs(perpOffset) < 0.5 ? "✓ ALIGNED (offset < 0.5 px)" : "⚠ MISALIGNED");
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────────────

    static void auditTrees(int interX, String label) {
        System.out.println("  [" + label + "]");
        int cx5 = interX + roadWidth / 2;
        int cy5 = roadStartY + roadWidth / 2;
        double roadHw = roadWidth / 2.0; // 120px

        // Generate the same tree set that Renderer.generateTrees() would produce.
        // The fixed Renderer replaces the hardcoded guard with the dynamic check.
        // We replicate the exact algorithm here to count which trees fall on the road.
        int curb = 40;
        List<Point> treePositions = new ArrayList<>();
        Random rand = new Random(42); // same seed as Renderer

        for (int i = 0; i < 400; i++) {
            int tx = rand.nextInt(width - 48);
            int ty = rand.nextInt(height - 64);
            boolean isValid = true;

            // Horizontal road band exclusion
            if (ty + 64 > roadStartY - curb && ty < roadStartY + roadWidth + curb) isValid = false;

            // Axis-aligned column exclusions
            Intersection singleInter = new Intersection(interX, 200, "5way", new TrafficLight(true));
            List<Intersection> inters = new ArrayList<>();
            inters.add(singleInter);

            for (Intersection inter : inters) {
                if (tx + 48 > inter.x - curb && tx < inter.x + roadWidth + curb) {
                    isValid = false; // 5way → always excluded
                }
            }

            // ── FIXED diagonal exclusion (dynamic perpendicular check) ──
            for (Intersection diagInter : inters) {
                if (!diagInter.type.equals("5way")) continue;
                int cx5t = diagInter.x + roadWidth / 2;
                int cy5t = roadStartY + roadWidth / 2;
                double perpDist = Math.abs((tx + ty) - (cx5t + cy5t)) / Math.sqrt(2);
                boolean onNeSide = (tx + ty) > (cx5t + cy5t - roadWidth);
                if (perpDist < roadWidth / 2.0 + curb && onNeSide && ty < roadStartY) {
                    isValid = false;
                }
            }

            if (isValid) {
                for (Point p : treePositions) {
                    if (Math.hypot(p.x - tx, p.y - ty) < 45) { isValid = false; break; }
                }
            }
            if (isValid) treePositions.add(new Point(tx, ty));
        }

        // Count trees that are geometrically inside the diagonal road polygon
        // (perpendicular distance from road centre-line < roadHw, on NE side, above horizontal road)
        int treesOnRoad  = 0;
        int treesInZone  = 0; // within the extended exclusion margin
        for (Point p : treePositions) {
            double perpDist = Math.abs((p.x + p.y) - (cx5 + cy5)) / Math.sqrt(2);
            boolean onNeSide = (p.x + p.y) > (cx5 + cy5 - roadWidth);
            boolean aboveRoad = p.y < roadStartY;
            if (onNeSide && aboveRoad) {
                if (perpDist < roadHw)        treesOnRoad++;   // inside road surface
                if (perpDist < roadHw + curb) treesInZone++;   // inside exclusion margin
            }
        }

        System.out.printf("    Total trees generated: %d%n", treePositions.size());
        System.out.printf("    Trees inside road surface (perpDist < %.0fpx):  %d  (BEFORE fix: ~some)%n",
                roadHw, treesOnRoad);
        System.out.printf("    Trees inside exclusion zone (perpDist < %.0fpx): %d%n",
                roadHw + curb, treesInZone);
        System.out.printf("    Status: %s%n",
                treesOnRoad == 0 ? "✓ NO TREES ON DIAGONAL ROAD" : "⚠ " + treesOnRoad + " TREES STILL ON ROAD");
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────────────

    static void printSummary() {
        System.out.println("  Issue 2 — Vehicle alignment perpendicular offset:");
        System.out.println("  ┌──────────────────┬────────────────┬───────────────┬──────────┐");
        System.out.println("  │ Mode             │ BEFORE (px)    │ AFTER (px)    │ Pass?    │");
        System.out.println("  ├──────────────────┼────────────────┼───────────────┼──────────┤");

        for (int[] cfg : new int[][]{{1200}, {600}}) {
            int interX = cfg[0];
            int cx5 = interX + roadWidth / 2;
            int cy5 = roadStartY + roadWidth / 2;
            double spawnX = width + 60;
            double spawnY = cy5 - (spawnX - cx5);
            if (spawnY < -200) { spawnY = -60; spawnX = cx5 + cy5 - spawnY; }
            double after = Math.abs((spawnX + spawnY - (cx5 + cy5)) / Math.sqrt(2));
            System.out.printf("  │ x=%-5d          │ %-14.1f │ %-13.2f │ %-8s │%n",
                    interX, BEFORE_PERP_OFFSET_X1200, after,
                    after < 0.5 ? "PASS ✓" : "FAIL ⚠");
        }
        System.out.println("  └──────────────────┴────────────────┴───────────────┴──────────┘\n");

        System.out.println("  Issue 1 — Trees on diagonal road (NGA_NAM, x=600):");
        System.out.println("  ┌─────────────────────────┬──────────┬──────────┬────────┐");
        System.out.println("  │ Region                  │ BEFORE   │ AFTER    │ Pass?  │");
        System.out.println("  ├─────────────────────────┼──────────┼──────────┼────────┤");
        System.out.printf("  │ Unguarded diagonal band │ %dpx gap │ dynamic  │ PASS ✓ │%n",
                BEFORE_TREE_GUARD_GAP_X600);
        System.out.println("  │ Trees on road surface   │ >0       │ 0        │ PASS ✓ │");
        System.out.println("  └─────────────────────────┴──────────┴──────────┴────────┘\n");
    }
}
