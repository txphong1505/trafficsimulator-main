package core;

import core.constants.Direction;
import core.controller.TrafficController;
import core.environment.Intersection;
import core.environment.TrafficLight;
import core.vehicles.Vehicle;

import java.util.*;

/**
 * IsolationVerifier v2 — proves centering AND isolation.
 *
 * For each map mode it runs 1 200 ticks and reports:
 *   • active intersection count and their X positions
 *   • spawn directions and vehicle X/Y ranges
 *   • SOUTHWEST spawn coordinates (NGA_NAM) to confirm adaptive formula
 *   • that vertical spawns land in the [activeX .. activeX+240] column only
 *   • stop-line probe: sample vehicles stopped at a red light and print
 *     front-bumper position vs. expected stop line (inter.x ± offset)
 */
public class IsolationVerifier {

    private static final int ROAD_WIDTH   = 240;
    private static final int ROAD_START_Y = 220;
    private static final int WIDTH        = 1600;
    private static final int HEIGHT       = 900;
    private static final int TICKS        = 1200;
    private static final int CENTER_X     = 600;  // canonical centre

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  MAP MODE CENTERING + ISOLATION VERIFICATION v2");
        System.out.println("=================================================================\n");

        runMode("TONG_HOP",
                buildIntersections(150, 600, 1200),
                -1);

        runMode("NGA_BA  (3-way centred at x=600)",
                buildSingle(600, "3way", false),
                600);

        runMode("NGA_TU  (4-way centred at x=600)",
                buildSingle(600, "4way", false),
                600);

        runMode("NGA_NAM (5-way centred at x=600)",
                buildSingle(600, "5way", true),
                600);

        System.out.println("=================================================================");
        System.out.println("  DONE");
        System.out.println("=================================================================");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<Intersection> buildIntersections(int x3, int x4, int x5) {
        List<Intersection> list = new ArrayList<>();
        list.add(new Intersection(x3, 200, "3way", new TrafficLight(false)));
        list.add(new Intersection(x4, 200, "4way", new TrafficLight(false)));
        list.add(new Intersection(x5, 200, "5way", new TrafficLight(true)));
        return list;
    }

    private static List<Intersection> buildSingle(int x, String type, boolean is5way) {
        List<Intersection> list = new ArrayList<>();
        list.add(new Intersection(x, 200, type, new TrafficLight(is5way)));
        return list;
    }

    // ── main runner ──────────────────────────────────────────────────────────

    private static void runMode(String label, List<Intersection> intersections, int activeX) {

        System.out.println("─── " + label + " ───");

        List<Vehicle> vehicles = new ArrayList<>();
        TrafficController controller = new TrafficController(intersections, vehicles);

        // Counters & accumulators
        Map<Direction, Integer> dirTicks = new LinkedHashMap<>();
        double minVX = Double.MAX_VALUE, maxVX = -Double.MAX_VALUE;
        double minVY = Double.MAX_VALUE, maxVY = -Double.MAX_VALUE;
        int colViolations = 0;   // vertical vehicles outside expected column
        int oldColSeen    = 0;   // vertical vehicles in original off-centre column(s)

        // Diagonal spawn tracking (NGA_NAM / TONG_HOP)
        List<double[]> swSpawnCoords = new ArrayList<>();

        // Stop-line probe — sample first 5 red-stopped vehicles
        List<String> stopProbes = new ArrayList<>();

        // Previous vehicle set to detect new additions (cheap spawn detection)
        Set<Integer> prevIds = new HashSet<>();

        for (int tick = 1; tick <= TICKS; tick++) {
            for (Intersection inter : intersections) inter.light.update();
            if (Math.random() < 0.09) controller.spawnVehicle();
            controller.updateAll();

            List<Vehicle> snap = new ArrayList<>(vehicles);

            // Detect newly spawned SOUTHWEST vehicles (first tick they appear)
            if (intersections.stream().anyMatch(i -> i.type.equals("5way"))) {
                for (Vehicle v : snap) {
                    int id = System.identityHashCode(v);
                    if (!prevIds.contains(id) && v.getDirection() == Direction.SOUTHWEST) {
                        swSpawnCoords.add(new double[]{v.getX(), v.getY()});
                    }
                }
            }
            prevIds.clear();
            for (Vehicle v : snap) prevIds.add(System.identityHashCode(v));

            for (Vehicle v : snap) {
                Direction d = v.getDirection();
                dirTicks.merge(d, 1, Integer::sum);
                double vx = v.getX(), vy = v.getY();
                if (vx < minVX) minVX = vx;
                if (vx > maxVX) maxVX = vx;
                if (vy < minVY) minVY = vy;
                if (vy > maxVY) maxVY = vy;

                // Vertical column check — only meaningful for isolated modes
                if (activeX >= 0 && (d == Direction.NORTH || d == Direction.SOUTH)) {
                    // Inside the road band?
                    if (vy >= ROAD_START_Y - 50 && vy <= ROAD_START_Y + ROAD_WIDTH + HEIGHT) {
                        boolean inColumn = vx >= activeX - 5 && vx <= activeX + ROAD_WIDTH + 5;
                        if (!inColumn) colViolations++;
                        // Check if vehicle is in an OLD off-centre column (150 or 1200)
                        if (vx >= 145 && vx <= 395 && activeX != 150) oldColSeen++;
                        if (vx >= 1195 && vx <= 1445 && activeX != 1200) oldColSeen++;
                    }
                }

                // Stop-line probe: red-stopped, in stop zone
                if (stopProbes.size() < 5 && v.getSpeed() == 0
                        && (d == Direction.EAST || d == Direction.WEST
                            || d == Direction.NORTH || d == Direction.SOUTH)) {
                    if (controller.isWaitingForLight(v)) {
                        double frontX = (d == Direction.EAST) ? vx + v.getBodyWidth() : vx;
                        double frontY = (d == Direction.SOUTH) ? vy + v.getBodyHeight() : vy;
                        for (Intersection inter : intersections) {
                            double expectedLine = getExpectedStopLine(d, inter);
                            double coord = (d == Direction.EAST || d == Direction.WEST) ? frontX : frontY;
                            double err = coord - expectedLine;
                            String key = v.getName() + "_" + System.identityHashCode(v);
                            String probe = String.format(
                                "  [STOP Tick=%4d] %-5s dir=%-6s front=%7.1f  stopLine=%7.1f  err=%+.1f",
                                tick, v.getName(), d, coord, expectedLine, err);
                            if (!stopProbes.contains(probe)) stopProbes.add(probe);
                        }
                    }
                }
            }
        }

        // ── Report ───────────────────────────────────────────────────────────

        System.out.println("  Active intersections:");
        for (Intersection inter : intersections)
            System.out.printf("    type=%-5s  x=%-5d  (range x=[%d..%d])%n",
                    inter.type, inter.x, inter.x, inter.x + ROAD_WIDTH);

        System.out.println("\n  Spawn directions (vehicle-ticks):");
        dirTicks.forEach((d, n) -> System.out.printf("    %-12s %d%n", d, n));

        System.out.printf("%n  Vehicle X range: [%.0f .. %.0f]%n", minVX, maxVX);
        System.out.printf("  Vehicle Y range: [%.0f .. %.0f]%n", minVY, maxVY);

        if (!swSpawnCoords.isEmpty()) {
            System.out.println("\n  SOUTHWEST spawn coords (first " + Math.min(5, swSpawnCoords.size()) + "):");
            for (int i = 0; i < Math.min(5, swSpawnCoords.size()); i++) {
                double[] c = swSpawnCoords.get(i);
                System.out.printf("    spawn=(%.0f, %.0f)%n", c[0], c[1]);
            }
        }

        if (activeX >= 0) {
            System.out.println("\n  Centering / isolation checks:");
            System.out.println("    Vertical column violations:  " + colViolations
                    + (colViolations == 0 ? " ✓" : " ⚠ UNEXPECTED"));
            System.out.println("    Vehicles at old off-centre columns: " + oldColSeen
                    + (oldColSeen == 0 ? " ✓" : " ⚠ UNEXPECTED"));

            boolean hasDiag = dirTicks.containsKey(Direction.SOUTHWEST);
            boolean is5way  = intersections.stream().anyMatch(i -> i.type.equals("5way"));
            if (is5way)
                System.out.println("    SOUTHWEST (diagonal) route: " + (hasDiag ? "ACTIVE ✓" : "absent ⚠"));
            else
                System.out.println("    SOUTHWEST (diagonal) route correctly absent: "
                        + (!hasDiag ? "✓" : "⚠ unexpected diagonal seen"));
        }

        if (!stopProbes.isEmpty()) {
            System.out.println("\n  Stop-line probes (red-light stops):");
            stopProbes.forEach(System.out::println);
        } else {
            System.out.println("\n  Stop-line probes: (no red-stopped vehicles sampled this run)");
        }
        System.out.println();
    }

    private static double getExpectedStopLine(Direction d, Intersection inter) {
        // Mirrors isInStopZone() in TrafficController exactly
        switch (d) {
            case EAST:  return inter.x - 40;
            case WEST:  return inter.x + ROAD_WIDTH + 40;
            case SOUTH: return ROAD_START_Y - 40;
            case NORTH: return ROAD_START_Y + ROAD_WIDTH + 40;
            default:    return Double.NaN;
        }
    }
}
