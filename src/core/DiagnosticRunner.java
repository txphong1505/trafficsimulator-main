package core;

import core.constants.Direction;
import core.controller.TrafficController;
import core.environment.Intersection;
import core.environment.TrafficLight;
import core.vehicles.Vehicle;

import java.awt.Rectangle;
import java.util.*;

public class DiagnosticRunner {

    private static final int WIDTH = 1600, HEIGHT = 900;
    private static final int ROAD_WIDTH = 240, ROAD_START_Y = 220;

    // Crosswalk geometry derived directly from Renderer.java lines 281-293:
    // Bottom crosswalk: fillRect(inter.x + off, roadStartY + roadWidth + 10, 14,
    // 30)
    // → starts at Y = roadStartY + roadWidth + 10 = 220 + 240 + 10 = 470
    // → ends at Y = 470 + 30 = 500
    // Top crosswalk: fillRect(inter.x + off, roadStartY - 40, 14, 30)
    // → starts at Y = roadStartY - 40 = 220 - 40 = 180
    // → ends at Y = 180 + 30 = 210
    // Left crosswalk: fillRect(inter.x - 40, roadStartY + off, 30, 14)
    // → starts at X = inter.x - 40
    // → ends at X = inter.x - 40 + 30 = inter.x - 10
    // Right crosswalk: fillRect(inter.x + roadWidth + 10, roadStartY + off, 30, 14)
    // → starts at X = inter.x + roadWidth + 10
    // → ends at X = inter.x + roadWidth + 40

    private static final int CROSSWALK_BOTTOM_START_Y = ROAD_START_Y + ROAD_WIDTH + 10; // 470
    private static final int CROSSWALK_TOP_END_Y = ROAD_START_Y - 40 + 30; // 210
    // Left crosswalk far edge (vehicle coming EAST must stop before inter.x - 40)
    private static final int CROSSWALK_LEFT_START_X_OFFSET = -40; // inter.x - 40
    // Right crosswalk far edge (vehicle coming WEST must stop after inter.x +
    // roadWidth + 40)
    private static final int CROSSWALK_RIGHT_END_X_OFFSET = +40; // inter.x + roadWidth + 40

    static int collisionCount = 0;
    static int deadlockCount = 0;
    static Set<String> loggedDeadlocks = new HashSet<>();
    static Set<String> loggedCollisions = new HashSet<>();

    // ── PROBE 1: Stop-line geometry ───────────────────────────────────────────
    private static final Map<String, Boolean> stopLogged = new HashMap<>();
    private static int stopProbeCount = 0;

    private static void probeStopLine(int tick, Vehicle v, List<Intersection> intersections) {
        if (v.getSpeed() != 0 || stopProbeCount >= 20)
            return;
        String logKey = v.getName() + "_" + System.identityHashCode(v);
        if (stopLogged.containsKey(logKey))
            return;

        Direction d = v.getDirection();
        if (d == Direction.NORTHEAST || d == Direction.SOUTHWEST)
            return;

        double frontX = v.getX(), frontY = v.getY();
        if (d == Direction.EAST)
            frontX += v.getBodyWidth();
        else if (d == Direction.SOUTH)
            frontY += v.getBodyHeight();

        for (Intersection inter : intersections) {
            if (!inter.light.canGo(d)) {
                double mathStopLine = Double.NaN, visualStopLine = Double.NaN;
                boolean relevant = false;

                if (d == Direction.EAST) {
                    mathStopLine = inter.x; // math boundary
                    visualStopLine = inter.x + CROSSWALK_LEFT_START_X_OFFSET; // 40px before inter.x
                    relevant = Math.abs(frontX - mathStopLine) < 100;
                } else if (d == Direction.WEST) {
                    mathStopLine = inter.x + ROAD_WIDTH;
                    visualStopLine = inter.x + ROAD_WIDTH + CROSSWALK_RIGHT_END_X_OFFSET;
                    relevant = Math.abs(frontX - mathStopLine) < 100;
                } else if (d == Direction.SOUTH) {
                    mathStopLine = ROAD_START_Y;
                    visualStopLine = CROSSWALK_TOP_END_Y; // 210 = top crosswalk bottom edge
                    relevant = v.getX() >= inter.x && v.getX() <= inter.x + ROAD_WIDTH
                            && Math.abs(frontY - mathStopLine) < 100;
                } else if (d == Direction.NORTH) {
                    mathStopLine = ROAD_START_Y + ROAD_WIDTH; // 460
                    visualStopLine = CROSSWALK_BOTTOM_START_Y; // 470
                    relevant = v.getX() >= inter.x && v.getX() <= inter.x + ROAD_WIDTH
                            && Math.abs(frontY - mathStopLine) < 100;
                }

                if (relevant && !Double.isNaN(mathStopLine)) {
                    double mathErr = frontX != Double.NaN && d == Direction.EAST || d == Direction.WEST
                            ? frontX - mathStopLine
                            : frontY - mathStopLine;
                    double visualErr = d == Direction.EAST || d == Direction.WEST
                            ? frontX - visualStopLine
                            : frontY - visualStopLine;
                    stopLogged.put(logKey, true);
                    stopProbeCount++;
                    System.out.printf(
                            "[STOP_PROBE Tick=%d] %-5s dir=%-9s bW=%2d bH=%2d spd=%.0f" +
                                    " | front=%6.1f mathLine=%6.1f mathErr=%+.1f | visLine=%6.1f visErr=%+.1f%n",
                            tick, v.getName(), d,
                            v.getBodyWidth(), v.getBodyHeight(), v.getOriginalSpeed(),
                            (d == Direction.EAST || d == Direction.WEST ? frontX : frontY),
                            mathStopLine, mathErr, visualStopLine, visualErr);
                    break;
                }
            }
        }
    }

    // ── PROBE 2: Diagonal tailgating (NORTHEAST / SOUTHWEST) ─────────────────
    // Logs the pixel gap between the nearest diagonal pair and the effective
    // predRect
    private static int diagProbeCount = 0;
    private static final Set<String> diagLogged = new HashSet<>();

    private static void probeDiagonalGap(int tick, Vehicle currentV, List<Vehicle> vehicles) {
        if (diagProbeCount >= 20)
            return;
        Direction d = currentV.getDirection();
        if (d != Direction.NORTHEAST && d != Direction.SOUTHWEST)
            return;

        double myCx = currentV.getX() + currentV.getBodyWidth() / 2.0;
        double myCy = currentV.getY() + currentV.getBodyHeight() / 2.0;

        for (Vehicle other : vehicles) {
            if (other == currentV || other.getDirection() != d)
                continue;
            String key = Math.min(System.identityHashCode(currentV), System.identityHashCode(other))
                    + "_" + Math.max(System.identityHashCode(currentV), System.identityHashCode(other));
            if (diagLogged.contains(key))
                continue;

            double otherCx = other.getX() + other.getBodyWidth() / 2.0;
            double otherCy = other.getY() + other.getBodyHeight() / 2.0;
            double diagDist = Math.hypot(otherCx - myCx, otherCy - myCy);

            // Measure effective hitbox gap (center-to-center minus half-body of each)
            double myHalfBody = Math.hypot(currentV.getBodyWidth() / 2.0, currentV.getBodyHeight() / 2.0);
            double otrHalfBody = Math.hypot(other.getBodyWidth() / 2.0, other.getBodyHeight() / 2.0);
            double pixelGap = diagDist - myHalfBody - otrHalfBody;

            // Log the predRect for threshold=35 (the stop threshold)
            Rectangle predRect35 = currentV.getPredictedHitbox(35);
            Rectangle otherHitbox = other.getShrinkHitbox(4);
            boolean pred35Hits = predRect35.intersects(otherHitbox);

            // Also check threshold=250 (far lookahead)
            Rectangle predRect250 = currentV.getPredictedHitbox(250);
            boolean pred250Hits = predRect250.intersects(otherHitbox);

            diagLogged.add(key);
            diagProbeCount++;
            System.out.printf(
                    "[DIAG_PROBE Tick=%d] %-5s dir=%s | myBox=%dx%d otherBox=%dx%d" +
                            " | center2center=%.1f pixelGap=%.1f | pred35=%s pred250=%s%n",
                    tick, currentV.getName(), d,
                    currentV.getBodyWidth(), currentV.getBodyHeight(),
                    other.getBodyWidth(), other.getBodyHeight(),
                    diagDist, pixelGap,
                    pred35Hits ? "HIT" : "miss",
                    pred250Hits ? "HIT" : "miss");
            if (diagProbeCount >= 20)
                break;
        }
    }

    // ── PROBE 3: BASIC-mode render dimensions (intersection deformation) ──────
    // Intersections at x=150, x=600, x=1200 (roadWidth=240, roadStartY=220)
    private static int renderProbeCount = 0;
    private static final Set<Integer> renderLogged = new HashSet<>();

    private static void probeRenderDimensions(int tick, Vehicle v, List<Intersection> intersections) {
        if (renderProbeCount >= 20)
            return;
        int id = System.identityHashCode(v);
        if (renderLogged.contains(id))
            return;

        int bw = v.getBodyWidth(), bh = v.getBodyHeight();
        boolean inIntersection = false;
        for (Intersection inter : intersections) {
            Rectangle interBox = new Rectangle(inter.x, ROAD_START_Y, ROAD_WIDTH, ROAD_WIDTH);
            if (interBox.intersects(v.getHitbox())) {
                inIntersection = true;
                break;
            }
        }

        // In BASIC mode, draw() calls fillRect((int)x,(int)y, bw, bh)
        // renderWidth == bw, renderHeight == bh — same as bodyWidth/Height.
        // If bw==bh it's a square (pre-fix diagonal bug).
        // Report everything so we can see if any deformation exists post-fix.
        renderLogged.add(id);
        renderProbeCount++;
        System.out.printf(
                "[RENDER_PROBE Tick=%d] %-5s dir=%-9s | bodyW=%d bodyH=%d renderW=%d renderH=%d" +
                        " | inIntersection=%s graphicMode=%s square=%s%n",
                tick, v.getName(), v.getDirection(),
                bw, bh, bw, bh, // renderW/H == bodyW/H in BASIC mode
                inIntersection ? "YES" : "no",
                Vehicle.graphicMode ? "GRAPHIC" : "BASIC",
                (bw == bh) ? "YES(BUG)" : "no");
    }

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  SMART CITY TRAFFIC SIMULATION - DIAGNOSTIC REPORT v4");
        System.out.println("====================================================================");
        System.out.println();
        System.out.println("Renderer crosswalk geometry (from Renderer.java):");
        System.out.println(
                "  Bottom crosswalk Y: [" + CROSSWALK_BOTTOM_START_Y + ", " + (CROSSWALK_BOTTOM_START_Y + 30) + "]");
        System.out.println("  Top    crosswalk Y: [" + (ROAD_START_Y - 40) + ", " + CROSSWALK_TOP_END_Y + "]");
        System.out.println("  Left   crosswalk X: [inter.x-40, inter.x-10]");
        System.out.println(
                "  Right  crosswalk X: [inter.x+" + (ROAD_WIDTH + 10) + ", inter.x+" + (ROAD_WIDTH + 40) + "]");
        System.out.println();

        System.out.println("== PHASE 1-3: Multi-probe run ==");
        runProbeRun(3000, 0.08);
        System.out.println();

        System.out.println("--- STRESS TEST 1: Normal (5000 ticks) ---");
        runTest(5000, 0.03, 0.0, 1);
        System.out.println();

        System.out.println("--- STRESS TEST 2: High Traffic (5000 ticks) ---");
        runTest(5000, 0.06, 0.0, 2);
        System.out.println();

        System.out.println("====================================================================");
        System.out.println("  AGGREGATE");
        System.out.println("====================================================================");
        System.out.println("Collisions: " + collisionCount + "  Deadlocks: " + deadlockCount);
        System.out.println("====================================================================");
    }

    private static void runProbeRun(int totalTicks, double spawnRate) {
        stopLogged.clear();
        stopProbeCount = 0;
        diagLogged.clear();
        diagProbeCount = 0;
        renderLogged.clear();
        renderProbeCount = 0;

        List<Vehicle> vehicles = new ArrayList<>();
        List<Intersection> intersections = new ArrayList<>();
        TrafficLight l3 = new TrafficLight(false);
        TrafficLight l4 = new TrafficLight(false);
        TrafficLight l5 = new TrafficLight(true);
        intersections.add(new Intersection(150, 200, "3way", l3));
        intersections.add(new Intersection(600, 200, "4way", l4));
        intersections.add(new Intersection(1200, 200, "5way", l5));

        TrafficController controller = new TrafficController(intersections, vehicles);
        Vehicle.graphicMode = false; // Force BASIC mode for render probe

        for (int tick = 1; tick <= totalTicks; tick++) {
            for (Intersection inter : intersections)
                inter.light.update();
            if (Math.random() < spawnRate)
                controller.spawnVehicle();
            controller.updateAll();

            List<Vehicle> snap = new ArrayList<>(vehicles);
            for (Vehicle v : snap) {
                probeStopLine(tick, v, intersections);
                probeDiagonalGap(tick, v, snap);
                probeRenderDimensions(tick, v, intersections);
            }

            boolean done = stopProbeCount >= 12 && diagProbeCount >= 5 && renderProbeCount >= 20;
            if (done && tick > 500) {
                System.out.println("  [Probes complete at tick " + tick + "]");
                break;
            }
        }
        Vehicle.graphicMode = true;
    }

    private static void runTest(int totalTicks, double spawnRate, double emgBoost, int testNum) {
        List<Vehicle> vehicles = new ArrayList<>();
        List<Intersection> intersections = new ArrayList<>();
        TrafficLight l3 = new TrafficLight(false);
        TrafficLight l4 = new TrafficLight(false);
        TrafficLight l5 = new TrafficLight(true);
        intersections.add(new Intersection(150, 200, "3way", l3));
        intersections.add(new Intersection(600, 200, "4way", l4));
        intersections.add(new Intersection(1200, 200, "5way", l5));

        TrafficController controller = new TrafficController(intersections, vehicles);
        int localCol = 0, localDead = 0, localMax = 0;
        Map<Integer, Integer> localStopped = new HashMap<>();

        for (int tick = 1; tick <= totalTicks; tick++) {
            for (Intersection inter : intersections)
                inter.light.update();
            double eff = spawnRate + (emgBoost > 0 && Math.random() < emgBoost ? spawnRate : 0);
            if (Math.random() < eff)
                controller.spawnVehicle();
            controller.updateAll();
            if (vehicles.size() > localMax)
                localMax = vehicles.size();

            List<Vehicle> vList = new ArrayList<>(vehicles);
            for (int i = 0; i < vList.size(); i++)
                for (int j = i + 1; j < vList.size(); j++) {
                    Vehicle a = vList.get(i), b = vList.get(j);
                    if (a.getShrinkHitbox(3).intersects(b.getShrinkHitbox(3)) && a.getDirection() != b.getDirection()) {
                        String k = Math.min(System.identityHashCode(a), System.identityHashCode(b)) + "_"
                                + Math.max(System.identityHashCode(a), System.identityHashCode(b));
                        if (loggedCollisions.add(k)) {
                            localCol++;
                            if (localCol <= 5)
                                System.out.printf("  [Tick %d] COLLISION: %s(%s) <-> %s(%s)%n",
                                        tick, a.getName(), a.getDirection(), b.getName(), b.getDirection());
                        }
                    }
                }
            for (Vehicle v : vList) {
                int id = System.identityHashCode(v);
                if (v.getSpeed() == 0 && !controller.isWaitingForLight(v)) {
                    localStopped.merge(id, 1, Integer::sum);
                    if (localStopped.getOrDefault(id, 0) == 101) {
                        if (loggedDeadlocks.add(String.valueOf(id))) {
                            localDead++;
                            if (localDead <= 5)
                                System.out.printf("  [Tick %d] DEADLOCK: %s at (%.0f,%.0f) dir=%s%n",
                                        tick, v.getName(), v.getX(), v.getY(), v.getDirection());
                        }
                    }
                } else {
                    localStopped.put(id, 0);
                }
            }
            if (tick % 1000 == 0)
                System.out.printf("  [Tick %d] v=%d col=%d dead=%d%n",
                        tick, vehicles.size(), localCol, localDead);
        }
        System.out.printf("  Test%d: Collisions=%d Deadlocks=%d MaxVehicles=%d%n",
                testNum, localCol, localDead, localMax);
        collisionCount += localCol;
        deadlockCount += localDead;
        loggedCollisions.clear();
        loggedDeadlocks.clear();
    }
}
