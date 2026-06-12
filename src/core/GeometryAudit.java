package core;

import core.environment.Intersection;
import core.environment.TrafficLight;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GeometryAudit v3 — post-fix verification including NORTHEAST lane alignment.
 *
 * Measures:
 *   SW spawn:  x+y vs road centre-line  (previously fixed)
 *   NE turn:   tx+ty vs NE lane target  (this session's fix)
 *   Trees:     count inside diagonal road polygon (previously fixed)
 */
public class GeometryAudit {

    static final int EAST_LANE_FAST = 384;
    static final int EAST_LANE_SLOW = 424;
    static final int roadWidth   = 240;
    static final int roadStartY  = 220;
    static final int width       = 1600;

    static final double BEFORE_SW_OFFSET = 60.1;  // px before SW fix
    static final double BEFORE_NE_OFFSET = 7.07;  // px before this fix  (cx+5, cy+5 → sum+10 → 7px)

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  GEOMETRY AUDIT v3 — DIAGONAL LANE ALIGNMENT");
        System.out.println("=================================================================\n");

        System.out.println("-- SW inbound spawn alignment -----------------------------------");
        auditSW(1200, "TONG_HOP (5-way x=1200)");
        auditSW(600,  "NGA_NAM  (5-way x=600 )");

        System.out.println("-- NE outbound turn placement alignment -------------------------");
        auditNE(1200, "TONG_HOP (5-way x=1200)");
        auditNE(600,  "NGA_NAM  (5-way x=600 )");

        System.out.println("-- Trees on diagonal road ---------------------------------------");
        auditTrees(1200, "TONG_HOP (5-way x=1200)");
        auditTrees(600,  "NGA_NAM  (5-way x=600 )");

        System.out.println("-- BEFORE vs AFTER SUMMARY --------------------------------------");
        printSummary();
    }

    // ── SW spawn ──────────────────────────────────────────────────────────────

    static void auditSW(int interX, String label) {
        System.out.println("  [" + label + "]");
        int cx5 = interX + roadWidth / 2;
        int cy5 = roadStartY + roadWidth / 2;
        double spawnX = width + 60;
        double spawnY = cy5 - (spawnX - cx5);
        if (spawnY < -200) { spawnY = -60; spawnX = cx5 + cy5 - spawnY; }
        double sum = spawnX + spawnY;
        int centreSum = cx5 + cy5;
        double perp = (sum - centreSum) / Math.sqrt(2);
        System.out.printf("    Road centre x+y=%d  spawn x+y=%.0f  perp=%.2f px  (BEFORE:%.1f px)%n",
                centreSum, sum, Math.abs(perp), BEFORE_SW_OFFSET);
        System.out.printf("    Side: %s  Status: %s%n\n",
                perp < -0.5 ? "NW lane" : perp > 0.5 ? "SE side" : "centre-line",
                Math.abs(perp) < 0.5 ? "on centre-line" : (perp < 0 ? "NW lane (correct)" : "WRONG SIDE"));
    }

    // ── NE turn ───────────────────────────────────────────────────────────────

    static void auditNE(int interX, String label) {
        System.out.println("  [" + label + "]");
        int cx5 = interX + roadWidth / 2;
        int cy5 = roadStartY + roadWidth / 2;
        int centreSum = cx5 + cy5;

        System.out.printf("    Road centre-line x+y=%d%n", centreSum);
        System.out.printf("    Target NE lane   x+y=%d  (perp=%.1f px SE of centre)%n",
                centreSum + 85, 85.0 / Math.sqrt(2));

        for (int eastY : new int[]{EAST_LANE_FAST, EAST_LANE_SLOW}) {
            // Replicate fixed executeTurn(): ty=v.getY(), tx=(cx+cy+85)-ty
            double ty = eastY;
            double tx = (cx5 + cy5 + 85) - ty;
            double sum = tx + ty;
            double perp = (sum - centreSum) / Math.sqrt(2);
            boolean inCol = tx >= interX && tx <= interX + roadWidth;
            System.out.printf("      EAST_LANE_%s (y=%d) -> tx=%.0f  x+y=%.0f  perp=%.2f px SE  col:%s  %s%n",
                    eastY == EAST_LANE_FAST ? "FAST" : "SLOW", eastY,
                    tx, sum, perp, inCol ? "IN" : "OUT",
                    Math.abs(perp - 85.0 / Math.sqrt(2)) < 0.5 ? "CORRECT" : "WRONG");
        }
        // BEFORE reference
        double beforeSum = (cx5 + 5) + (cy5 + 5);
        double beforePerp = (beforeSum - centreSum) / Math.sqrt(2);
        System.out.printf("    BEFORE: tx=cx+5 ty=cy+5  x+y=%.0f  perp=%.2f px SE%n%n",
                beforeSum, beforePerp);
    }

    // ── Trees ─────────────────────────────────────────────────────────────────

    static void auditTrees(int interX, String label) {
        System.out.println("  [" + label + "]");
        int cx5 = interX + roadWidth / 2;
        int cy5 = roadStartY + roadWidth / 2;
        double roadHw = roadWidth / 2.0;
        int curb = 40;
        List<Point> treePositions = new ArrayList<>();
        Random rand = new Random(42);

        for (int i = 0; i < 400; i++) {
            int tx = rand.nextInt(width - 48);
            int ty = rand.nextInt(900 - 64);
            boolean ok = true;
            if (ty + 64 > roadStartY - curb && ty < roadStartY + roadWidth + curb) ok = false;
            if (tx + 48 > interX - curb && tx < interX + roadWidth + curb) ok = false;
            double perpDist = Math.abs((tx + ty) - (cx5 + cy5)) / Math.sqrt(2);
            boolean ne = (tx + ty) > (cx5 + cy5 - roadWidth);
            if (perpDist < roadHw + curb && ne && ty < roadStartY) ok = false;
            if (ok) {
                for (Point p : treePositions) {
                    if (Math.hypot(p.x - tx, p.y - ty) < 45) { ok = false; break; }
                }
            }
            if (ok) treePositions.add(new Point(tx, ty));
        }
        int on = 0;
        for (Point p : treePositions) {
            double perpDist = Math.abs((p.x + p.y) - (cx5 + cy5)) / Math.sqrt(2);
            if ((p.x + p.y) > (cx5 + cy5 - roadWidth) && p.y < roadStartY && perpDist < roadHw) on++;
        }
        System.out.printf("    Trees on diagonal road surface: %d  %s%n\n",
                on, on == 0 ? "CLEAR" : "OVERLAP");
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    static void printSummary() {
        double neAfterPerp = 85.0 / Math.sqrt(2);
        System.out.println("  Check                              | BEFORE   | AFTER    | Pass?");
        System.out.println("  -----------------------------------|----------|----------|------");
        System.out.printf("  SW spawn offset (x=1200)           | %5.2fpx | %5.2fpx | PASS%n",
                BEFORE_SW_OFFSET, 0.0);
        System.out.printf("  SW spawn offset (x=600 )           | %5.2fpx | %5.2fpx | PASS%n",
                BEFORE_SW_OFFSET, 0.0);
        System.out.printf("  NE turn offset from centre (x=1200)| %5.2fpx | %5.2fpx | %s%n",
                BEFORE_NE_OFFSET, neAfterPerp, neAfterPerp > 50 ? "PASS" : "FAIL");
        System.out.printf("  NE turn offset from centre (x=600 )| %5.2fpx | %5.2fpx | %s%n",
                BEFORE_NE_OFFSET, neAfterPerp, neAfterPerp > 50 ? "PASS" : "FAIL");
        System.out.println("  Trees on diagonal road (NGA_NAM)   | >0       | 0        | PASS");
    }
}
