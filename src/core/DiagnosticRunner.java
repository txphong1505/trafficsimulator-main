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

    private static final int WEST_LANE_SLOW = 224, WEST_LANE_FAST = 264, WEST_LANE_EMG = 304;
    private static final int EAST_LANE_EMG = 344, EAST_LANE_FAST = 384, EAST_LANE_SLOW = 424;

    static int collisionCount = 0;
    static int deadlockCount = 0;
    static int laneDeviationCount = 0;
    static int outOfBoundsCount = 0;
    static int totalVehiclesProcessed = 0;
    static int maxConcurrentVehicles = 0;

    static Map<Integer, Integer> stoppedTicks = new HashMap<>();
    static Set<String> loggedDeadlocks = new HashSet<>();
    static Set<String> loggedCollisions = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  SMART CITY TRAFFIC SIMULATION - DIAGNOSTIC REPORT");
        System.out.println("====================================================================");
        System.out.println();

        System.out.println("--- TEST 1: Normal Traffic (5000 ticks, spawnRate=0.03) ---");
        runTest(5000, 0.03, 0.0, 1);
        System.out.println();

        System.out.println("--- TEST 2: High Traffic (5000 ticks, spawnRate=0.06) ---");
        runTest(5000, 0.06, 0.0, 2);
        System.out.println();

        System.out.println("--- TEST 3: Emergency Heavy (5000 ticks, spawnRate=0.06, emgBoost=0.5) ---");
        runTest(5000, 0.06, 0.5, 3);
        System.out.println();

        System.out.println("====================================================================");
        System.out.println("  AGGREGATE SUMMARY");
        System.out.println("====================================================================");
        System.out.println("Total Collisions:       " + collisionCount);
        System.out.println("Total Deadlocks (>100): " + deadlockCount);
        System.out.println("Total Lane Deviations:  " + laneDeviationCount);
        System.out.println("Total Out-of-Bounds:    " + outOfBoundsCount);
        System.out.println("====================================================================");
    }

    private static void runTest(int totalTicks, double spawnRate, double emgBoost, int testNum) {
        List<Vehicle> vehicles = new ArrayList<>();
        List<Intersection> intersections = new ArrayList<>();

        TrafficLight light3 = new TrafficLight(false);
        TrafficLight light4 = new TrafficLight(false);
        TrafficLight light5 = new TrafficLight(true);
        intersections.add(new Intersection(150, 200, "3way", light3));
        intersections.add(new Intersection(600, 200, "4way", light4));
        intersections.add(new Intersection(1200, 200, "5way", light5));

        TrafficController controller = new TrafficController(intersections, vehicles);

        int localCollisions = 0;
        int localDeadlocks = 0;
        int localLaneDeviations = 0;
        int localOOB = 0;
        int localMaxVehicles = 0;
        int localTotalSpawned = 0;

        Map<Integer, Integer> localStoppedTicks = new HashMap<>();
        Map<Integer, Boolean> localWaitingState = new HashMap<>();

        for (int tick = 1; tick <= totalTicks; tick++) {
            for (Intersection inter : intersections) {
                inter.light.update();
            }

            double effectiveRate = spawnRate;
            if (emgBoost > 0 && Math.random() < emgBoost) {
                effectiveRate = spawnRate * 2;
            }
            if (Math.random() < effectiveRate) {
                int prevSize = vehicles.size();
                controller.spawnVehicle();
                if (vehicles.size() > prevSize) localTotalSpawned++;
            }

            controller.updateAll();

            if (vehicles.size() > localMaxVehicles) localMaxVehicles = vehicles.size();

            List<Vehicle> vList = new ArrayList<>(vehicles);

            // --- COLLISION DETECTION ---
            for (int i = 0; i < vList.size(); i++) {
                for (int j = i + 1; j < vList.size(); j++) {
                    Vehicle a = vList.get(i);
                    Vehicle b = vList.get(j);
                    Rectangle ra = a.getShrinkHitbox(3);
                    Rectangle rb = b.getShrinkHitbox(3);
                    if (ra.intersects(rb)) {
                        if (a.getDirection() == b.getDirection()) continue;
                        boolean aEmg = a.getName().equals("Ambu") || a.getName().equals("Fire");
                        boolean bEmg = b.getName().equals("Ambu") || b.getName().equals("Fire");

                        String key = Math.min(System.identityHashCode(a), System.identityHashCode(b)) + "_" +
                                     Math.max(System.identityHashCode(a), System.identityHashCode(b));

                        if (!loggedCollisions.contains(key)) {
                            loggedCollisions.add(key);
                            localCollisions++;
                            if (localCollisions <= 20) {
                                System.out.printf("  [Tick %d] COLLISION: %s (%.0f,%.0f %s) [ra=%s] <-> %s (%.0f,%.0f %s) [rb=%s]%n",
                                    tick, a.getName(), a.getX(), a.getY(), a.getDirection(), ra.toString(),
                                    b.getName(), b.getX(), b.getY(), b.getDirection(), rb.toString());
                            }
                        }
                    }
                }
            }

            // --- DEADLOCK DETECTION (with category) ---
            for (Vehicle v : vList) {
                int id = System.identityHashCode(v);
                if (v.getSpeed() == 0 && !controller.isWaitingForLight(v)) {
                    localStoppedTicks.merge(id, 1, Integer::sum);
                    localWaitingState.put(id, v.isWaitingToTurn());
                    int stopped = localStoppedTicks.get(id);
                    if (stopped == 101) {
                        if (!loggedDeadlocks.contains(String.valueOf(id))) {
                            loggedDeadlocks.add(String.valueOf(id));
                            localDeadlocks++;
                            String cause = v.isWaitingToTurn() ? "WAITING_TURN" : "BLOCKED";
                            if (localDeadlocks <= 30) {
                                System.out.printf("  [Tick %d] DEADLOCK(%s): %s#%d at (%.0f,%.0f) dir=%s intent=%s hasTurned=%b%n",
                                    tick, cause, v.getName(), id % 10000, v.getX(), v.getY(),
                                    v.getDirection(), v.getTurnIntent(), v.hasTurned());
                            }
                        }
                    }
                } else if (v.getSpeed() > 0) {
                    localStoppedTicks.put(id, 0);
                }
            }

            // --- LANE DEVIATION DETECTION ---
            for (Vehicle v : vList) {
                if (v.hasTurned()) continue;
                Direction d = v.getDirection();
                double vy = v.getY();
                double vx = v.getX();

                if (d == Direction.EAST) {
                    boolean onLane = (vy == EAST_LANE_SLOW || vy == EAST_LANE_FAST || vy == EAST_LANE_EMG);
                    if (!onLane && vx > -50 && vx < WIDTH + 50) {
                        boolean nearIntersection = false;
                        for (Intersection inter : intersections) {
                            if (vx > inter.x - 100 && vx < inter.x + ROAD_WIDTH + 100) {
                                nearIntersection = true;
                                break;
                            }
                        }
                        if (!nearIntersection) {
                            localLaneDeviations++;
                            if (localLaneDeviations <= 10) {
                                System.out.printf("  [Tick %d] LANE_DEV: %s EAST at y=%.1f (expected 424/384/344)%n",
                                    tick, v.getName(), vy);
                            }
                        }
                    }
                } else if (d == Direction.WEST) {
                    boolean onLane = (vy == WEST_LANE_SLOW || vy == WEST_LANE_FAST || vy == WEST_LANE_EMG);
                    if (!onLane && vx > -50 && vx < WIDTH + 50) {
                        boolean nearIntersection = false;
                        for (Intersection inter : intersections) {
                            if (vx > inter.x - 100 && vx < inter.x + ROAD_WIDTH + 100) {
                                nearIntersection = true;
                                break;
                            }
                        }
                        if (!nearIntersection) {
                            localLaneDeviations++;
                            if (localLaneDeviations <= 10) {
                                System.out.printf("  [Tick %d] LANE_DEV: %s WEST at y=%.1f (expected 224/264/304)%n",
                                    tick, v.getName(), vy);
                            }
                        }
                    }
                }
            }

            // --- OUT OF BOUNDS DETECTION ---
            for (Vehicle v : vList) {
                double vx = v.getX(), vy2 = v.getY();
                if (vx < -500 || vx > WIDTH + 500 || vy2 < -500 || vy2 > HEIGHT + 500) {
                    localOOB++;
                    if (localOOB <= 10) {
                        System.out.printf("  [Tick %d] OUT_OF_BOUNDS: %s at (%.0f,%.0f) dir=%s%n",
                            tick, v.getName(), vx, vy2, v.getDirection());
                    }
                }
            }

            if (tick % 1000 == 0) {
                System.out.printf("  [Tick %d] vehicles=%d, collisions=%d, deadlocks=%d, laneDevs=%d, oob=%d%n",
                    tick, vehicles.size(), localCollisions, localDeadlocks, localLaneDeviations, localOOB);
            }
        }

        System.out.println("  --- Test " + testNum + " Results ---");
        System.out.println("  Collisions:       " + localCollisions);
        System.out.println("  Deadlocks (>100): " + localDeadlocks);
        System.out.println("  Lane Deviations:  " + localLaneDeviations);
        System.out.println("  Out-of-Bounds:    " + localOOB);
        System.out.println("  Max Vehicles:     " + localMaxVehicles);
        System.out.println("  Total Spawned:    " + localTotalSpawned);

        collisionCount += localCollisions;
        deadlockCount += localDeadlocks;
        laneDeviationCount += localLaneDeviations;
        outOfBoundsCount += localOOB;
        totalVehiclesProcessed += localTotalSpawned;
        if (localMaxVehicles > maxConcurrentVehicles) maxConcurrentVehicles = localMaxVehicles;

        loggedCollisions.clear();
        loggedDeadlocks.clear();
    }
}
