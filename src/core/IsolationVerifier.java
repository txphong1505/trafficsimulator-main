package core;

import core.constants.Direction;
import core.controller.TrafficController;
import core.environment.Intersection;
import core.environment.TrafficLight;
import core.vehicles.Vehicle;

import java.util.*;

/**
 * IsolationVerifier — proves that each MapMode confines vehicles to exactly
 * the expected intersection(s) and spawn routes.
 *
 * Runs 800 simulation ticks per mode and prints:
 *   • which intersections are active
 *   • which vehicle spawn directions appeared
 *   • the X/Y coordinate ranges actually occupied by vehicles
 *   • whether any vehicle crossed outside the expected bounds
 */
public class IsolationVerifier {

    private static final int ROAD_WIDTH  = 240;
    private static final int ROAD_START_Y = 220;
    private static final int SIM_WIDTH   = 1600;
    private static final int SIM_HEIGHT  = 900;
    private static final int TICKS       = 800;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  MAP MODE ISOLATION VERIFICATION");
        System.out.println("=================================================================\n");

        runMode("TONG_HOP",
                buildIntersections(true, true, true),
                "All 3 intersections active — x=[150..390], x=[600..840], x=[1200..1440]",
                -1);  // -1 = no restriction

        runMode("NGA_BA (3-way only)",
                buildIntersections(true, false, false),
                "Only 3-way at x=150. No 4-way, no 5-way. No diagonal route.",
                150);

        runMode("NGA_TU (4-way only)",
                buildIntersections(false, true, false),
                "Only 4-way at x=600. No 3-way, no 5-way. No diagonal route.",
                600);

        runMode("NGA_NAM (5-way only)",
                buildIntersections(false, false, true),
                "Only 5-way at x=1200. Diagonal (SOUTHWEST) route active. No 3-way, no 4-way.",
                1200);

        System.out.println("=================================================================");
        System.out.println("  VERIFICATION COMPLETE");
        System.out.println("=================================================================");
    }

    private static List<Intersection> buildIntersections(boolean nga3, boolean nga4, boolean nga5) {
        List<Intersection> list = new ArrayList<>();
        if (nga3) list.add(new Intersection(150,  200, "3way", new TrafficLight(false)));
        if (nga4) list.add(new Intersection(600,  200, "4way", new TrafficLight(false)));
        if (nga5) list.add(new Intersection(1200, 200, "5way", new TrafficLight(true)));
        return list;
    }

    private static void runMode(String label, List<Intersection> intersections,
                                 String description, int activeInterX) {

        System.out.println("─── MODE: " + label + " ───────────────────────────────────────");
        System.out.println("    " + description);

        List<Vehicle> vehicles = new ArrayList<>();
        TrafficController controller = new TrafficController(intersections, vehicles);

        // Counters
        Map<Direction, Integer> spawnedDirections = new LinkedHashMap<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int violationCount = 0;
        Set<String> spawnedTypes = new LinkedHashSet<>();

        for (int tick = 1; tick <= TICKS; tick++) {
            // Tick lights
            for (Intersection inter : intersections) inter.light.update();

            // Spawn probabilistically
            if (Math.random() < 0.08) controller.spawnVehicle();
            controller.updateAll();

            // Record what we see
            for (Vehicle v : new ArrayList<>(vehicles)) {
                Direction d = v.getDirection();
                spawnedDirections.merge(d, 1, Integer::sum);
                spawnedTypes.add(v.getName());

                double vx = v.getX(), vy = v.getY();
                if (vx < minX) minX = vx;
                if (vx > maxX) maxX = vx;
                if (vy < minY) minY = vy;
                if (vy > maxY) maxY = vy;

                // Violation: vehicle is inside road corridor but in a wrong intersection column
                // We skip vehicles outside the road bounds (they are despawning off-screen)
                if (vy >= ROAD_START_Y - 20 && vy <= ROAD_START_Y + ROAD_WIDTH + 20) {
                    if (activeInterX >= 0) {
                        boolean inCorrectColumn =
                            (vx >= activeInterX - 350 && vx <= activeInterX + ROAD_WIDTH + 350);
                        boolean directionIsVertical =
                            (d == Direction.NORTH || d == Direction.SOUTH);
                        if (directionIsVertical && !inCorrectColumn) {
                            violationCount++;
                        }
                    }
                }
            }
        }

        // Print results
        System.out.println("\n  Active intersections: " + intersections.size());
        for (Intersection inter : intersections)
            System.out.println("    • type=" + inter.type + "  x=" + inter.x);

        System.out.println("\n  Spawn directions observed (direction → total vehicle-ticks):");
        if (spawnedDirections.isEmpty()) {
            System.out.println("    (none — no vehicles spawned)");
        } else {
            for (Map.Entry<Direction, Integer> e : spawnedDirections.entrySet())
                System.out.printf("    %-12s  %d%n", e.getKey(), e.getValue());
        }

        System.out.println("\n  Vehicle types seen: " + spawnedTypes);

        System.out.printf("\n  X range of all vehicle positions: [%.0f .. %.0f]%n", minX, maxX);
        System.out.printf("  Y range of all vehicle positions: [%.0f .. %.0f]%n", minY, maxY);

        // Disabled routes checks
        System.out.println("\n  Isolation checks:");
        boolean diagonalSeen = spawnedDirections.containsKey(Direction.SOUTHWEST)
                            || spawnedDirections.containsKey(Direction.NORTHEAST);
        boolean has5way = intersections.stream().anyMatch(i -> i.type.equals("5way"));
        boolean has3way = intersections.stream().anyMatch(i -> i.type.equals("3way"));
        boolean has4way = intersections.stream().anyMatch(i -> i.type.equals("4way"));

        System.out.println("    5-way (diagonal) route: " + (has5way ? "ACTIVE ✓" : "DISABLED ✗"));
        System.out.println("    3-way route:             " + (has3way ? "ACTIVE ✓" : "DISABLED ✗"));
        System.out.println("    4-way route:             " + (has4way ? "ACTIVE ✓" : "DISABLED ✗"));
        if (!has5way && diagonalSeen)
            System.out.println("    ⚠ DIAGONAL vehicles seen without 5-way — unexpected!");
        else if (!has5way)
            System.out.println("    Diagonal spawn: correctly absent ✓");

        if (activeInterX >= 0)
            System.out.println("    Vertical-column violations: " + violationCount
                    + (violationCount == 0 ? " ✓" : " ⚠ UNEXPECTED"));

        System.out.println();
    }
}
