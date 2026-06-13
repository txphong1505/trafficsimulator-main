package core.controller;

import core.constants.Direction;
import core.environment.Intersection;
import core.vehicles.*;
import core.strategies.*;

import java.awt.Rectangle;
import java.util.*;
import java.lang.Math;

public class TrafficController {
    private List<Intersection> intersections;
    private List<Vehicle> vehicles;
    private final int width = 1600, height = 900;
    private int roadWidth = 240;
    private int roadStartY = 220;
    private int maxCapacity = 40;

    public int WEST_LANE_SLOW = 224, WEST_LANE_FAST = 264, WEST_LANE_EMG = 304;
    public int EAST_LANE_EMG = 344, EAST_LANE_FAST = 384, EAST_LANE_SLOW = 424;

    private int ticks = 0;
    private Map<Integer, Integer> laneChangeCooldown = new HashMap<>();
    private Set<Integer> justYielded = new HashSet<>();

    public TrafficController(List<Intersection> intersections, List<Vehicle> vehicles) {
        this.intersections = intersections;
        this.vehicles = vehicles;
    }

    private boolean canChangeLane(Vehicle v) {
        int last = laneChangeCooldown.getOrDefault(System.identityHashCode(v), -999);
        return (ticks - last) > 15;
    }

    public void spawnVehicle() {
        if (vehicles.size() >= maxCapacity)
            return;
        Random rand = new Random();
        if (rand.nextBoolean()) {
            spawnHorizontalVehicle();
        } else {
            spawnVerticalVehicle();
        }
    }

    private void spawnHorizontalVehicle() {
        List<Class<? extends Vehicle>> types = Arrays.asList(Car.class, Car.class, Car.class, Motorcycle.class,
                Bicycle.class, Ambulance.class, FireTruck.class);
        Class<? extends Vehicle> vehicleClass = types.get(new Random().nextInt(types.size()));

        Optional<Intersection> inter5 = intersections.stream().filter(i -> i.type.equals("5way")).findFirst();
        Random rand = new Random();
        if (inter5.isPresent() && rand.nextDouble() < 0.20) {
            Intersection inter = inter5.get();
            int cx5 = inter.x + roadWidth / 2;
            int cy5 = roadStartY + roadWidth / 2;
            // Spawn on the road centre-line: x + y = cx5 + cy5 (the 45° diagonal invariant).
            // A SOUTHWEST vehicle conserves x+y throughout its run (Δx=−Δy per updatePosition),
            // so the spawn point must satisfy spawnX + spawnY = cx5 + cy5 exactly.
            // Prefer the screen's right edge; fall back to top edge when right-edge Y < -200.
            double spawnX = width + 60;
            // Áp dụng offset -85 để xe đi vào làn bên phải (Tây Bắc của trục trung tâm)
            double spawnY = cy5 - (spawnX - cx5) - 85;
            if (spawnY < -200) {
                spawnY = -60;                        // top-edge exit
                // Đảm bảo tổng x + y luôn bằng cx5 + cy5 - 85
                spawnX = cx5 + cy5 - 85 - spawnY;
            }
            Direction dir = Direction.SOUTHWEST;
            for (Vehicle v : vehicles) {
                if (v.getDirection() == dir && Math.hypot(v.getX() - spawnX, v.getY() - spawnY) < 160)
                    return;
            }
            Vehicle newV = createVehicle(vehicleClass, spawnX, spawnY, dir);
            if (newV != null) {
                newV.setHasTurned(false);
                newV.setDriverStrategy(getStrategyForVehicle(newV));
                vehicles.add(newV);
            }
            return;
        }

        boolean isEast = rand.nextBoolean();
        Direction dir = isEast ? Direction.EAST : Direction.WEST;
        double spawnX = isEast ? -60 : width + 60;
        List<Integer> possibleLanes = new ArrayList<>();
        if (isEast) {
            if (vehicleClass == Ambulance.class || vehicleClass == FireTruck.class) {
                possibleLanes = Arrays.asList(EAST_LANE_FAST, EAST_LANE_EMG);
            } else if (vehicleClass == Car.class)
                possibleLanes = Arrays.asList(EAST_LANE_SLOW, EAST_LANE_FAST);
            else
                possibleLanes = Collections.singletonList(EAST_LANE_SLOW);
        } else {
            if (vehicleClass == Ambulance.class || vehicleClass == FireTruck.class) {
                possibleLanes = Arrays.asList(WEST_LANE_FAST, WEST_LANE_EMG);
            } else if (vehicleClass == Car.class)
                possibleLanes = Arrays.asList(WEST_LANE_SLOW, WEST_LANE_FAST);
            else
                possibleLanes = Collections.singletonList(WEST_LANE_SLOW);
        }
        double spawnY = possibleLanes.get(rand.nextInt(possibleLanes.size()));
        for (Vehicle v : vehicles) {
            if (v.getDirection() == dir && Math.abs(v.getY() - spawnY) < 1 && Math.abs(v.getX() - spawnX) < 160)
                return;
        }
        Vehicle newV = createVehicle(vehicleClass, spawnX, spawnY, dir);
        if (newV != null) {
            newV.setDriverStrategy(getStrategyForVehicle(newV));
            vehicles.add(newV);
        }
    }

    private void spawnVerticalVehicle() {
        if (vehicles.size() >= maxCapacity)
            return;
        List<Intersection> validIntersections = new ArrayList<>(intersections);
        if (validIntersections.isEmpty())
            return;
        Intersection inter = validIntersections.get(new Random().nextInt(validIntersections.size()));
        boolean isNorth = new Random().nextBoolean();
        if (!isNorth && inter.type.equals("3way"))
            return;

        Direction dir = isNorth ? Direction.NORTH : Direction.SOUTH;
        double spawnY = isNorth ? height + 60 : -60;

        List<Class<? extends Vehicle>> types = Arrays.asList(Car.class, Car.class, Car.class, Motorcycle.class,
                Bicycle.class, Ambulance.class, FireTruck.class);
        Class<? extends Vehicle> vehicleClass = types.get(new Random().nextInt(types.size()));

        List<Integer> possibleLanesX = new ArrayList<>();
        if (dir == Direction.SOUTH) {
            if (vehicleClass == Ambulance.class || vehicleClass == FireTruck.class) {
                possibleLanesX.add(getSouthFastX(inter));
                possibleLanesX.add(getSouthEmgX(inter));
            } else if (vehicleClass == Car.class) {
                possibleLanesX.add(getSouthSlowX(inter));
                possibleLanesX.add(getSouthFastX(inter));
            } else {
                possibleLanesX.add(getSouthSlowX(inter));
            }
        } else { // NORTH
            if (vehicleClass == Ambulance.class || vehicleClass == FireTruck.class) {
                possibleLanesX.add(getNorthFastX(inter));
                possibleLanesX.add(getNorthEmgX(inter));
            } else if (vehicleClass == Car.class) {
                possibleLanesX.add(getNorthSlowX(inter));
                possibleLanesX.add(getNorthFastX(inter));
            } else {
                possibleLanesX.add(getNorthSlowX(inter));
            }
        }
        double spawnX = possibleLanesX.get(new Random().nextInt(possibleLanesX.size()));

        for (Vehicle v : vehicles) {
            if (v.getDirection() == dir && Math.abs(v.getX() - spawnX) < 60 && Math.abs(v.getY() - spawnY) < 100) {
                return;
            }
        }
        Vehicle newV = createVehicle(vehicleClass, spawnX, spawnY, dir);
        if (newV != null) {
            newV.setDriverStrategy(getStrategyForVehicle(newV));
            if (dir == Direction.NORTH && inter.type.equals("3way")) {
                newV.setTurnIntent(new Random().nextBoolean() ? "LEFT" : "RIGHT");
            }
            vehicles.add(newV);
        }
    }

    private Vehicle createVehicle(Class<? extends Vehicle> clazz, double x, double y, Direction dir) {
        try {
            if (clazz == Car.class)
                return new Car(x, y, dir);
            if (clazz == Motorcycle.class)
                return new Motorcycle(x, y, dir);
            if (clazz == Bicycle.class)
                return new Bicycle(x, y, dir);
            if (clazz == Ambulance.class)
                return new Ambulance(x, y, dir);
            if (clazz == FireTruck.class)
                return new FireTruck(x, y, dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private DriverStrategy getStrategyForVehicle(Vehicle v) {
        if (v.getName().equals("Ambu") || v.getName().equals("Fire"))
            return new EmergencyDriver();
        if (v.getName().equals("Motor"))
            return new AggressiveDriver();
        return new NormalDriver();
    }

    private int getSouthSlowX(Intersection inter) {
        return inter.x + 4;
    }

    private int getSouthFastX(Intersection inter) {
        return inter.x + 44;
    }

    private int getSouthEmgX(Intersection inter) {
        return inter.x + 84;
    }

    private int getNorthSlowX(Intersection inter) {
        return inter.x + 204;
    }

    private int getNorthFastX(Intersection inter) {
        return inter.x + 164;
    }

    private int getNorthEmgX(Intersection inter) {
        return inter.x + 124;
    }

    private int getEastLaneForTurn(Intersection inter, Vehicle v) {
        if (v.getName().equals("Ambu") || v.getName().equals("Fire"))
            return EAST_LANE_EMG;
        if (v.getName().equals("Bike"))
            return EAST_LANE_SLOW;
        return EAST_LANE_FAST;
    }

    private int getWestLaneForTurn(Intersection inter, Vehicle v) {
        if (v.getName().equals("Ambu") || v.getName().equals("Fire"))
            return WEST_LANE_EMG;
        if (v.getName().equals("Bike"))
            return WEST_LANE_SLOW;
        return WEST_LANE_FAST;
    }

    // ======================== TÍNH PHỤ THUỘC THẤP (LOOSE COUPLING)
    // ========================
    // Giao diện gọn gàng hơn vì Controller không cần phải mổ xẻ nội tại của Vehicle
    private boolean isSpotOccupied(Vehicle v, double tx, double ty) {
        Rectangle testRect = new Rectangle((int) tx, (int) ty, v.getBodyWidth(), v.getBodyHeight());
        for (Vehicle o : vehicles) {
            if (o != v && testRect.intersects(o.getHitbox())) {
                if (o.isWaitingToTurn() || o.getSpeed() == 0) {
                    boolean vIsEmg = v.getName().equals("Ambu") || v.getName().equals("Fire");
                    boolean oIsEmg = o.getName().equals("Ambu") || o.getName().equals("Fire");
                    if (vIsEmg && !oIsEmg)
                        continue;
                    if (vIsEmg == oIsEmg && System.identityHashCode(v) > System.identityHashCode(o))
                        continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean isTurnSafe(Vehicle v) {
        Rectangle blindSpot = v.getBlindSpot(150, 60, 90, 2);
        if (blindSpot != null) {
            for (Vehicle other : vehicles) {
                if (other == v || other.getSpeed() == 0)
                    continue;
                if (other.getDirection() == v.getDirection()) {
                    if (blindSpot.intersects(other.getHitbox()))
                        return false;
                }
            }
        }
        return true;
    }
    // =======================================================================================

    public void executeTurn(Vehicle v) {
        v.setWaitingToTurn(false);
        // ── 5-way diagonal turns (EAST→NE keep snap; SW→EXIT now smooth) ────────
        for (Intersection inter : intersections) {
            if (inter.type.equals("5way")) {
                int cx = inter.x + roadWidth / 2;
                int cy = roadStartY + roadWidth / 2;
                // EAST → NORTHEAST (snap — geometry already fixed, keep as-is)
                if (v.getDirection() == Direction.EAST && v.getTurnIntent().equals("DIAGONAL") && !v.hasTurned()) {
                    if (Math.abs(v.getX() - (cx - 30)) <= v.getOriginalSpeed() * 2) {
                        double ty = v.getY();
                        double tx = (cx + cy + 85) - ty;
                        if (!isSpotOccupied(v, tx, ty)) {
                            v.setX(tx);
                            v.setY(ty);
                            v.setDirection(Direction.NORTHEAST);
                            v.setHasTurned(true);
                            v.setWaitingToTurn(false);
                            return;
                        }
                    }
                }
                // SOUTHWEST → WEST or SOUTH (smooth Bézier curve)
                if (v.getDirection() == Direction.SOUTHWEST && !v.hasTurned() && !v.isTurning()) {
                    if (Math.abs(v.getX() - (cx + 30)) <= v.getOriginalSpeed() * 5) {
                        Direction nextDir = new Random().nextBoolean() ? Direction.WEST : Direction.SOUTH;
                        // P2: 80 px past the original snap position, in the exit direction
                        double p2x = (nextDir == Direction.WEST) ? cx - 120 : inter.x + 64;
                        double p2y = (nextDir == Direction.WEST) ? WEST_LANE_FAST : cy + 120;
                        double[] din  = dirVector(Direction.SOUTHWEST);
                        double[] dout = dirVector(nextDir);
                        double[] p1   = computeControlPoint(v.getX(), v.getY(), din, p2x, p2y, dout);
                        if (!isSpotOccupied(v, (int) p2x, (int) p2y)) {
                            v.setWaitingToTurn(false);
                            v.beginTurn(v.getX(), v.getY(), p1[0], p1[1], p2x, p2y, nextDir);
                        } else {
                            v.setWaitingToTurn(true);
                            v.setSpeed(0);
                        }
                        return;
                    }
                }
            }
        }

        // ── 90° turns (horizontal ↔ vertical) ────────────────────────────────────
        for (Intersection inter : intersections) {
            if (v.hasTurned() || v.isTurning())
                break;

            if (v.getDirection() == Direction.NORTH && inter.type.equals("3way")
                    && v.getTurnIntent().equals("STRAIGHT")) {
                if (v.getX() >= inter.x && v.getX() <= inter.x + roadWidth) {
                    v.setTurnIntent(new Random().nextBoolean() ? "LEFT" : "RIGHT");
                }
            }

            if (v.getTurnIntent().equals("STRAIGHT"))
                continue;

            // ── EAST / WEST → NORTH / SOUTH ──────────────────────────────────────
            if (v.getDirection() == Direction.EAST || v.getDirection() == Direction.WEST) {
                if ((inter.type.equals("3way") && v.getTurnIntent().equals("LEFT")
                        && v.getDirection() == Direction.EAST) ||
                        (inter.type.equals("3way") && v.getTurnIntent().equals("RIGHT")
                                && v.getDirection() == Direction.WEST))
                    continue;

                int[] southLanes = { getSouthSlowX(inter), getSouthFastX(inter), getSouthEmgX(inter) };
                int[] northLanes = { getNorthSlowX(inter), getNorthFastX(inter), getNorthEmgX(inter) };
                Integer targetX = null;
                Direction targetDir = null;
                boolean isEmergency = v.getName().equals("Ambu") || v.getName().equals("Fire");

                if (v.getDirection() == Direction.EAST) {
                    if (v.getTurnIntent().equals("RIGHT")) {
                        targetX = isEmergency ? southLanes[1]
                                : (v.getName().equals("Bike") ? southLanes[0] : southLanes[1]);
                        targetDir = Direction.SOUTH;
                    } else if (v.getTurnIntent().equals("LEFT")) {
                        targetX = isEmergency ? northLanes[1]
                                : (v.getName().equals("Bike") ? northLanes[0] : northLanes[1]);
                        targetDir = Direction.NORTH;
                    }
                } else { // WEST
                    if (v.getTurnIntent().equals("RIGHT")) {
                        targetX = isEmergency ? northLanes[1]
                                : (v.getName().equals("Bike") ? northLanes[0] : northLanes[1]);
                        targetDir = Direction.NORTH;
                    } else if (v.getTurnIntent().equals("LEFT")) {
                        targetX = isEmergency ? southLanes[1]
                                : (v.getName().equals("Bike") ? southLanes[0] : southLanes[1]);
                        targetDir = Direction.SOUTH;
                    }
                }

                if (targetX != null && targetDir != null) {
                    // ── Smooth-turn trigger: fires as soon as the front bumper enters the
                    //    intersection column, giving the Bézier arc the full intersection width
                    //    to develop.  Mathematical proof: when the fallback condition
                    //    |v.getX()-targetX| ≤ speed would fire, frontX is always already
                    //    inside [inter.x+10 .. inter.x+roadWidth), so inEntry is always true
                    //    and the return below makes the old snap permanently unreachable.
                    if (!v.hasTurned() && !v.isTurning()) {
                        double frontX = (v.getDirection() == Direction.EAST)
                                ? v.getX() + v.getBodyWidth()   // right edge = forward for EAST
                                : v.getX();                      // left  edge = forward for WEST
                        boolean inEntry = (v.getDirection() == Direction.EAST)
                                ? frontX >= inter.x + 10 && frontX < inter.x + roadWidth
                                : frontX <= inter.x + roadWidth - 10 && frontX > inter.x;
                        if (inEntry) {
                            if (isTurnSafe(v)) {
                                double p0x = v.getX(), p0y = v.getY();
                                double p2x = targetX;
                                double p2y = (targetDir == Direction.SOUTH)
                                        ? roadStartY + roadWidth + 100
                                        : roadStartY - 100;
                                double[] din  = dirVector(v.getDirection());
                                double[] dout = dirVector(targetDir);
                                double[] p1   = computeControlPoint(p0x, p0y, din, p2x, p2y, dout);
                                v.setWaitingToTurn(false);
                                v.beginTurn(p0x, p0y, p1[0], p1[1], p2x, p2y, targetDir);
                            } else {
                                // Cannot turn yet — stop at intersection entry and wait.
                                // The return below is the key: it prevents the old fallback
                                // snap (v.setX(targetX)) from ever firing.
                                v.setWaitingToTurn(true);
                                v.setSpeed(0);
                            }
                            return; // always return when inEntry active
                        }
                        // Vehicle has not yet entered this intersection's turning zone.
                    }
                    // No fallback snap.  If the vehicle drove straight through without
                    // ever being able to turn safely, it exits the intersection and is
                    // despawned when it leaves the map — no discrete coordinate jump.
                }

            // ── SOUTH / NORTH → EAST / WEST ──────────────────────────────────────
            } else if (v.getDirection() == Direction.SOUTH || v.getDirection() == Direction.NORTH) {
                if (v.getX() < inter.x || v.getX() > inter.x + roadWidth)
                    continue;

                int targetY = -1;
                Direction targetDir = null;
                if (v.getDirection() == Direction.SOUTH) {
                    if (v.getTurnIntent().equals("RIGHT")) {
                        targetY = getWestLaneForTurn(inter, v);
                        targetDir = Direction.WEST;
                    } else if (v.getTurnIntent().equals("LEFT")) {
                        targetY = getEastLaneForTurn(inter, v);
                        targetDir = Direction.EAST;
                    }
                } else { // NORTH
                    if (v.getTurnIntent().equals("RIGHT")) {
                        targetY = getEastLaneForTurn(inter, v);
                        targetDir = Direction.EAST;
                    } else if (v.getTurnIntent().equals("LEFT")) {
                        targetY = getWestLaneForTurn(inter, v);
                        targetDir = Direction.WEST;
                    }
                }

                if (targetY != -1 && targetDir != null) {
                    // ── Smooth-turn trigger (same design as H→V above) ──────────────
                    if (!v.hasTurned() && !v.isTurning()) {
                        double frontY = (v.getDirection() == Direction.SOUTH)
                                ? v.getY() + v.getBodyHeight()
                                : v.getY();
                        boolean inEntry = (v.getDirection() == Direction.SOUTH)
                                ? frontY >= roadStartY + 10 && frontY < roadStartY + roadWidth
                                : frontY <= roadStartY + roadWidth - 10 && frontY > roadStartY;
                        boolean inColumn = v.getX() >= inter.x && v.getX() <= inter.x + roadWidth;
                        if (inEntry && inColumn) {
                            if (isTurnSafe(v)) {
                                double p0x = v.getX(), p0y = v.getY();
                                double p2y = targetY;
                                double p2x = (targetDir == Direction.EAST)
                                        ? inter.x + roadWidth + 100
                                        : inter.x - 100;
                                double[] din  = dirVector(v.getDirection());
                                double[] dout = dirVector(targetDir);
                                double[] p1   = computeControlPoint(p0x, p0y, din, p2x, p2y, dout);
                                v.setWaitingToTurn(false);
                                v.beginTurn(p0x, p0y, p1[0], p1[1], p2x, p2y, targetDir);
                            } else {
                                v.setWaitingToTurn(true);
                                v.setSpeed(0);
                            }
                            return; // always return when inEntry+inColumn active
                        }
                    }
                    // No fallback snap — same reasoning as H→V block above.
                }
            }
        }
    }

    // ── Bézier turn geometry helpers ─────────────────────────────────────────

    /**
     * Returns the unit-vector (dx, dy) corresponding to a Direction.
     */
    private double[] dirVector(Direction d) {
        switch (d) {
            case EAST:      return new double[]{ 1,       0      };
            case WEST:      return new double[]{-1,       0      };
            case SOUTH:     return new double[]{ 0,       1      };
            case NORTH:     return new double[]{ 0,      -1      };
            case NORTHEAST: return new double[]{ 0.7071, -0.7071 };
            case SOUTHWEST: return new double[]{-0.7071,  0.7071 };
            default:        return new double[]{ 1,       0      };
        }
    }

    /**
     * Computes the quadratic Bézier control point P1 for a turn from P0
     * travelling in direction {@code din} to exit point P2 leaving in direction
     * {@code dout}.
     * <p>
     * P1 is the intersection of:
     * <ul>
     *   <li>the ray from P0 in direction din  (entry tangent), and</li>
     *   <li>the ray from P2 in direction -dout (reverse exit tangent).</li>
     * </ul>
     * This guarantees that B'(0) ∥ din and B'(1) ∥ dout exactly.
     */
    private double[] computeControlPoint(double p0x, double p0y, double[] din,
                                          double p2x, double p2y, double[] dout) {
        // Solve: (P2 - P0) = s * din  +  t * dout
        // det = din.x * dout.y  -  din.y * dout.x
        double det = din[0] * dout[1] - din[1] * dout[0];
        if (Math.abs(det) < 1e-9) {
            // Directions are parallel → fall back to midpoint.
            return new double[]{ (p0x + p2x) / 2, (p0y + p2y) / 2 };
        }
        double dx = p2x - p0x, dy = p2y - p0y;
        double s = (dx * dout[1] - dy * dout[0]) / det;
        return new double[]{ p0x + s * din[0], p0y + s * din[1] };
    }

    public boolean checkSafeDistance(Vehicle currentV, int threshold) {
        Rectangle myActualRect = currentV.getShrinkHitbox(2);
        Rectangle predRect = currentV.getPredictedHitbox(threshold);
        double myCx = currentV.getX() + currentV.getBodyWidth() / 2.0;
        double myCy = currentV.getY() + currentV.getBodyHeight() / 2.0;

        for (Vehicle other : vehicles) {
            if (other == currentV)
                continue;
            // FIX-2: A turning vehicle that has been blocked for ≥20 consecutive ticks is
            // deadlocked (its arc and this vehicle's path are mutually blocking each other).
            // Allow cross-traffic to pass through its mid-arc hitbox so that both vehicles
            // can make progress.  The transient overlap (≤15 ticks) is preferable to an
            // irresolvable permanent stall.
            if (other.isTurning() && other.getTurnBlockedTicks() >= 20)
                continue;
            Rectangle otherRect = other.getShrinkHitbox(4);

            if (myActualRect.intersects(otherRect)) {
                boolean vEmg = currentV.getName().equals("Ambu") || currentV.getName().equals("Fire");
                boolean oEmg = other.getName().equals("Ambu") || other.getName().equals("Fire");
                if (vEmg && !oEmg)
                    continue;
                if (!vEmg && oEmg)
                    return false;

                // XỬ LÝ LỖI ĐÂM XUYÊN: Dựa vào hướng di chuyển để biết ai đi sau thì người đó
                // phải nhường
                if (currentV.getDirection() == other.getDirection()) {
                    boolean amIBehind = false;
                    switch (currentV.getDirection()) {
                        case EAST:
                            amIBehind = currentV.getX() < other.getX();
                            break;
                        case WEST:
                            amIBehind = currentV.getX() > other.getX();
                            break;
                        case SOUTH:
                            amIBehind = currentV.getY() < other.getY();
                            break;
                        case NORTH:
                            amIBehind = currentV.getY() > other.getY();
                            break;
                        case NORTHEAST:
                            amIBehind = currentV.getX() < other.getX();
                            break;
                        case SOUTHWEST:
                            amIBehind = currentV.getX() > other.getX();
                            break;
                    }
                    if (amIBehind)
                        return false; // Nếu đi sau, bắt buộc phải dừng

                    // Nếu lỡ trùng cả tọa độ, dùng HashCode phá bế tắc (1 xe đi, 1 xe đứng)
                    if (currentV.getX() == other.getX() && currentV.getY() == other.getY()) {
                        if (System.identityHashCode(currentV) < System.identityHashCode(other))
                            return false;
                    }
                } else {
                    // Nếu là 2 luồng cắt ngang nhau, dùng HashCode nhường ngẫu nhiên
                    if (System.identityHashCode(currentV) < System.identityHashCode(other))
                        return false;
                }
            }

            if (predRect.intersects(otherRect)) {
                double otherCx = other.getX() + other.getBodyWidth() / 2.0;
                double otherCy = other.getY() + other.getBodyHeight() / 2.0;
                double dx = otherCx - myCx, dy = otherCy - myCy;
                double vx = 0, vy = 0;
                switch (currentV.getDirection()) {
                    case EAST:
                        vx = 1;
                        break;
                    case WEST:
                        vx = -1;
                        break;
                    case SOUTH:
                        vy = 1;
                        break;
                    case NORTH:
                        vy = -1;
                        break;
                    case NORTHEAST:
                        vx = 0.7071;
                        vy = -0.7071;
                        break;
                    case SOUTHWEST:
                        vx = -0.7071;
                        vy = 0.7071;
                        break;
                }

                // Lateral guard: if the other vehicle is predominantly beside us (perpendicular
                // distance >> forward distance), it is in an adjacent lane, not blocking our
                // path.
                // Lateral component = total displacement minus the forward projection.
                double forwardProjection = dx * vx + dy * vy;
                double lateralDx = dx - forwardProjection * vx;
                double lateralDy = dy - forwardProjection * vy;
                double lateralDist = Math.sqrt(lateralDx * lateralDx + lateralDy * lateralDy);
                if (lateralDist > 28)
                    continue;

                if (forwardProjection > -0.1) {
                    return false;
                } else if (currentV.getDirection() != other.getDirection()) {
                    if (System.identityHashCode(currentV) < System.identityHashCode(other))
                        return false;
                }
            }
        }
        return true;
    }

    private boolean isLaneSafe(Vehicle currentV, double targetY) {
        Rectangle blindSpot = new Rectangle((int) currentV.getX() - 100, (int) targetY, currentV.getBodyWidth() + 200,
                currentV.getBodyHeight());
        for (Vehicle other : vehicles) {
            if (other != currentV && blindSpot.intersects(other.getHitbox()))
                return false;
        }
        return true;
    }

    private boolean isLaneSafeVertical(Vehicle currentV, double targetX) {
        Rectangle blindSpot = new Rectangle((int) targetX, (int) currentV.getY() - 100, currentV.getBodyWidth(),
                currentV.getBodyHeight() + 200);
        for (Vehicle other : vehicles) {
            if (other != currentV && blindSpot.intersects(other.getHitbox()))
                return false;
        }
        return true;
    }

    public void tryOvertake(Vehicle currentV) {
        if (!canChangeLane(currentV))
            return;
        Direction d = currentV.getDirection();
        if (d == Direction.NORTHEAST || d == Direction.SOUTHWEST)
            return;
        boolean isEmg = currentV.getName().equals("Ambu") || currentV.getName().equals("Fire");
        Double targetY = null, targetX = null;

        if (d == Direction.EAST) {
            if (currentV.getY() == EAST_LANE_SLOW) {
                if (isLaneSafe(currentV, EAST_LANE_FAST))
                    targetY = (double) EAST_LANE_FAST;
                else if (isEmg && isLaneSafe(currentV, EAST_LANE_EMG))
                    targetY = (double) EAST_LANE_EMG;
            } else if (currentV.getY() == EAST_LANE_FAST && isEmg && isLaneSafe(currentV, EAST_LANE_EMG))
                targetY = (double) EAST_LANE_EMG;
        } else if (d == Direction.WEST) {
            if (currentV.getY() == WEST_LANE_SLOW) {
                if (isLaneSafe(currentV, WEST_LANE_FAST))
                    targetY = (double) WEST_LANE_FAST;
                else if (isEmg && isLaneSafe(currentV, WEST_LANE_EMG))
                    targetY = (double) WEST_LANE_EMG;
            } else if (currentV.getY() == WEST_LANE_FAST && isEmg && isLaneSafe(currentV, WEST_LANE_EMG))
                targetY = (double) WEST_LANE_EMG;
        } else {
            Intersection inter = getApproachingIntersection(currentV);
            if (inter == null)
                return;
            double curX = currentV.getX();
            if (d == Direction.SOUTH) {
                if (curX == getSouthSlowX(inter)) {
                    if (isLaneSafeVertical(currentV, getSouthFastX(inter)))
                        targetX = (double) getSouthFastX(inter);
                    else if (isEmg && isLaneSafeVertical(currentV, getSouthEmgX(inter)))
                        targetX = (double) getSouthEmgX(inter);
                } else if (curX == getSouthFastX(inter) && isEmg && isLaneSafeVertical(currentV, getSouthEmgX(inter)))
                    targetX = (double) getSouthEmgX(inter);
            } else {
                if (curX == getNorthSlowX(inter)) {
                    if (isLaneSafeVertical(currentV, getNorthFastX(inter)))
                        targetX = (double) getNorthFastX(inter);
                    else if (isEmg && isLaneSafeVertical(currentV, getNorthEmgX(inter)))
                        targetX = (double) getNorthEmgX(inter);
                } else if (curX == getNorthFastX(inter) && isEmg && isLaneSafeVertical(currentV, getNorthEmgX(inter)))
                    targetX = (double) getNorthEmgX(inter);
            }
        }

        if (targetY != null) {
            currentV.setY(targetY);
            laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
        } else if (targetX != null) {
            currentV.setX(targetX);
            laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
        }
    }

    public void returnToSlowLane(Vehicle currentV) {
        if (justYielded.contains(System.identityHashCode(currentV)) || !canChangeLane(currentV))
            return;
        if (currentV.getName().equals("Ambu") || currentV.getName().equals("Fire"))
            return;
        Direction d = currentV.getDirection();
        if (d == Direction.NORTHEAST || d == Direction.SOUTHWEST)
            return;

        Double targetY = null, targetX = null;
        if (d == Direction.EAST) {
            if (currentV.getY() == EAST_LANE_EMG)
                targetY = (double) EAST_LANE_FAST;
            else if (currentV.getY() == EAST_LANE_FAST)
                targetY = (double) EAST_LANE_SLOW;
        } else if (d == Direction.WEST) {
            if (currentV.getY() == WEST_LANE_EMG)
                targetY = (double) WEST_LANE_FAST;
            else if (currentV.getY() == WEST_LANE_FAST)
                targetY = (double) WEST_LANE_SLOW;
        } else {
            Intersection inter = getApproachingIntersection(currentV);
            if (inter == null)
                return;
            double curX = currentV.getX();
            if (d == Direction.SOUTH) {
                if (curX == getSouthEmgX(inter))
                    targetX = (double) getSouthFastX(inter);
                else if (curX == getSouthFastX(inter))
                    targetX = (double) getSouthSlowX(inter);
            } else {
                if (curX == getNorthEmgX(inter))
                    targetX = (double) getNorthFastX(inter);
                else if (curX == getNorthFastX(inter))
                    targetX = (double) getNorthSlowX(inter);
            }
        }

        if (targetY != null && isLaneSafe(currentV, targetY)) {
            double oldY = currentV.getY();
            currentV.setY(targetY);
            if (!checkSafeDistance(currentV, 50))
                currentV.setY(oldY);
            else
                laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
        } else if (targetX != null && isLaneSafeVertical(currentV, targetX)) {
            double oldX = currentV.getX();
            currentV.setX(targetX);
            if (!checkSafeDistance(currentV, 50))
                currentV.setX(oldX);
            else
                laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
        }
    }

    private void yieldToEmergency(Vehicle currentV) {
        if (currentV.getName().equals("Ambu") || currentV.getName().equals("Fire"))
            return;
        Direction d = currentV.getDirection();
        if (d == Direction.NORTHEAST || d == Direction.SOUTHWEST)
            return;

        if (d == Direction.EAST || d == Direction.WEST) {
            int currentLane = -1;
            if (d == Direction.EAST) {
                if (currentV.getY() == EAST_LANE_FAST)
                    currentLane = 2;
                else if (currentV.getY() == EAST_LANE_EMG)
                    currentLane = 3;
            } else {
                if (currentV.getY() == WEST_LANE_FAST)
                    currentLane = 2;
                else if (currentV.getY() == WEST_LANE_EMG)
                    currentLane = 3;
            }
            if (currentLane == -1)
                return;

            for (Vehicle other : vehicles) {
                if (other == currentV || !(other.getName().equals("Ambu") || other.getName().equals("Fire")))
                    continue;
                if (other.getDirection() != d || Math.abs(currentV.getY() - other.getY()) >= 5)
                    continue;
                if ((d == Direction.EAST && other.getX() > currentV.getX())
                        || (d == Direction.WEST && other.getX() < currentV.getX()))
                    continue;
                if (Math.abs(currentV.getX() - other.getX()) > 150)
                    continue;

                int targetLane = currentLane - 1;
                double targetY = (d == Direction.EAST) ? ((targetLane == 1) ? EAST_LANE_SLOW : EAST_LANE_FAST)
                        : ((targetLane == 1) ? WEST_LANE_SLOW : WEST_LANE_FAST);
                if (isLaneSafe(currentV, targetY)) {
                    currentV.setY(targetY);
                    laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
                    justYielded.add(System.identityHashCode(currentV));
                    break;
                }
            }
        } else {
            Intersection inter = getApproachingIntersection(currentV);
            if (inter == null)
                return;
            int currentLane = -1;
            double curX = currentV.getX();
            if (d == Direction.SOUTH) {
                if (curX == getSouthFastX(inter))
                    currentLane = 2;
                else if (curX == getSouthEmgX(inter))
                    currentLane = 3;
            } else {
                if (curX == getNorthFastX(inter))
                    currentLane = 2;
                else if (curX == getNorthEmgX(inter))
                    currentLane = 3;
            }
            if (currentLane == -1)
                return;

            for (Vehicle other : vehicles) {
                if (other == currentV || !(other.getName().equals("Ambu") || other.getName().equals("Fire")))
                    continue;
                if (other.getDirection() != d || Math.abs(currentV.getX() - other.getX()) >= 5)
                    continue;
                if ((d == Direction.SOUTH && other.getY() < currentV.getY())
                        || (d == Direction.NORTH && other.getY() > currentV.getY()))
                    continue;
                if (Math.abs(currentV.getY() - other.getY()) > 150)
                    continue;

                int targetLane = currentLane - 1;
                double targetX = (d == Direction.SOUTH)
                        ? ((targetLane == 1) ? getSouthSlowX(inter) : getSouthFastX(inter))
                        : ((targetLane == 1) ? getNorthSlowX(inter) : getNorthFastX(inter));
                if (isLaneSafeVertical(currentV, targetX)) {
                    currentV.setX(targetX);
                    laneChangeCooldown.put(System.identityHashCode(currentV), ticks);
                    justYielded.add(System.identityHashCode(currentV));
                    break;
                }
            }
        }
    }

    private Intersection getApproachingIntersection(Vehicle v) {
        Direction d = v.getDirection();
        if (d == Direction.NORTHEAST)
            return null;

        double frontX = v.getX(), frontY = v.getY();
        if (d == Direction.EAST)
            frontX += v.getBodyWidth();
        else if (d == Direction.SOUTH)
            frontY += v.getBodyHeight();

        int stopDist = 280, westStopDist = 310, overshoot = 30;
        for (Intersection inter : intersections) {
            if (d == Direction.EAST && frontX >= inter.x - stopDist && frontX <= inter.x + overshoot)
                return inter;
            if (d == Direction.WEST && frontX <= inter.x + roadWidth + westStopDist
                    && frontX >= inter.x + roadWidth - overshoot)
                return inter;
            if (d == Direction.SOUTH && v.getX() >= inter.x && v.getX() <= inter.x + roadWidth
                    && frontY >= roadStartY - stopDist && frontY <= roadStartY + overshoot)
                return inter;
            if (d == Direction.NORTH && v.getX() >= inter.x && v.getX() <= inter.x + roadWidth
                    && frontY <= roadStartY + roadWidth + stopDist && frontY >= roadStartY + roadWidth - overshoot)
                return inter;
            if (d == Direction.SOUTHWEST && inter.type.equals("5way")) {
                int cx = inter.x + roadWidth / 2;
                double targetStopX = cx + 200;
                if (frontX <= targetStopX + stopDist && frontX >= targetStopX - overshoot)
                    return inter;
            }
        }
        return null;
    }

    private boolean isIntersectionClear(Vehicle currentV, Intersection inter) {
        if (inter.type.equals("4way") || inter.type.equals("5way"))
            return true;

        Rectangle interRect = new Rectangle(inter.x, roadStartY, roadWidth, roadWidth);
        for (Vehicle other : vehicles) {
            if (other == currentV)
                continue;
            if (interRect.intersects(other.getHitbox())) {
                if (currentV.getDirection() != other.getDirection()) {
                    boolean currEmg = currentV.getName().equals("Ambu") || currentV.getName().equals("Fire");
                    boolean otherEmg = other.getName().equals("Ambu") || other.getName().equals("Fire");
                    if (currEmg && !otherEmg)
                        continue;
                    if (!currEmg && otherEmg)
                        return false;
                    if (!currentV.getTurnIntent().equals("STRAIGHT") && other.getTurnIntent().equals("STRAIGHT"))
                        return false;

                    double centerX = inter.x + roadWidth / 2.0, centerY = roadStartY + roadWidth / 2.0;
                    double myDistToCenter = Math.hypot(currentV.getX() - centerX, currentV.getY() - centerY);
                    double otherDistToCenter = Math.hypot(other.getX() - centerX, other.getY() - centerY);

                    if (myDistToCenter > otherDistToCenter + 5)
                        return false;
                    else if (Math.abs(myDistToCenter - otherDistToCenter) <= 5 && currentV.getX() > other.getX())
                        return false;
                }
            }
        }
        return true;
    }

    // Returns true only when the vehicle's front bumper is within its own braking
    // window
    // of the physical stop line. The window is kept minimal (speed*2 + 10 px) so
    // that only
    // the vehicle that is actually AT the line brakes for the light; rear-queue
    // vehicles
    // are held by checkSafeDistance behind the stopped leader instead.
    private boolean isInStopZone(Vehicle v, Intersection inter) {
        Direction d = v.getDirection();
        double frontX = v.getX(), frontY = v.getY();
        if (d == Direction.EAST)
            frontX += v.getBodyWidth();
        else if (d == Direction.SOUTH)
            frontY += v.getBodyHeight();
        // NORTH front bumper is the top edge → frontY = v.getY() (no addition needed)
        // WEST front bumper is the left edge → frontX = v.getX() (no addition needed)

        // Minimal one-tick lookahead: the zone fires exactly one speed-step before the
        // stop
        // line, so the vehicle brakes on the same tick its front bumper would cross the
        // line.
        // Both buf and overshoot are bounded to speed+1 px, keeping the stopping error
        // < 1 px.
        int buf = (int) (v.getOriginalSpeed()) + 1;
        int overshoot = (int) (v.getOriginalSpeed()) + 1;

        // Crosswalk padding derived from Renderer.java:
        // EAST: left crosswalk stripe at [inter.x-40, inter.x-10] → stop at inter.x-40
        // WEST: right crosswalk stripe at [inter.x+roadWidth+10, inter.x+roadWidth+40]
        // → stop at inter.x+roadWidth+40
        // SOUTH: top crosswalk stripe at [roadStartY-40, roadStartY-10] → stop at
        // roadStartY-10
        // NORTH: bottom crosswalk stripe at [roadStartY+roadWidth+10,
        // roadStartY+roadWidth+40] → stop at roadStartY+roadWidth+10
        if (d == Direction.EAST) {
            double stopLine = inter.x - 40;
            return frontX >= stopLine - buf && frontX <= stopLine + overshoot;
        }
        if (d == Direction.WEST) {
            double stopLine = inter.x + roadWidth + 40;
            return frontX <= stopLine + buf && frontX >= stopLine - overshoot;
        }
        if (d == Direction.SOUTH) {
            if (v.getX() < inter.x || v.getX() > inter.x + roadWidth)
                return false;
            double stopLine = roadStartY - 40;
            return frontY >= stopLine - buf && frontY <= stopLine + overshoot;
        }
        if (d == Direction.NORTH) {
            if (v.getX() < inter.x || v.getX() > inter.x + roadWidth)
                return false;
            double stopLine = roadStartY + roadWidth + 40;
            return frontY <= stopLine + buf && frontY >= stopLine - overshoot;
        }
        if (d == Direction.SOUTHWEST && inter.type.equals("5way")) {
            int cx = inter.x + roadWidth / 2;
            double stopLine = cx + 200;
            return frontX <= stopLine + buf && frontX >= stopLine - overshoot;
        }
        return false;
    }

    private boolean canVehicleGo(Vehicle v) {
        Intersection approaching = getApproachingIntersection(v);
        if (approaching == null || v.getName().equals("Ambu") || v.getName().equals("Fire"))
            return true;
        // Only enforce the red/yellow light when the vehicle has reached the physical
        // stop line.
        // Cars that are further back in the queue have approaching != null (suppressing
        // tryOvertake)
        // but should not be braked by the light itself — the car directly in front will
        // stop them.
        if (!isInStopZone(v, approaching))
            return true;
        return approaching.light.canGo(v.getDirection()) && isIntersectionClear(v, approaching);
    }

    public void updateAll() {
        ticks++;
        for (Vehicle v : new java.util.ArrayList<>(vehicles)) {

            // ── Smooth-turn: a vehicle committed to a Bézier arc advances along it
            //    and skips all other logic (traffic light, safe-distance, overtake).
            //    This is physically appropriate — once a turn is begun it is committed.
            //    COLLISION GUARD: after advancing, if the new hitbox penetrates any
            //    non-turning vehicle, revert the position and turnT for this tick
            //    (arc is paused until the exit lane clears).
            if (v.isTurning()) {
                double savedX = v.getX(), savedY = v.getY(), savedT = v.getTurnT();
                boolean done = v.advanceTurn();
                if (!done) {
                    Rectangle myHitbox = v.getHitbox();
                    boolean blocked = false;
                    for (Vehicle other : vehicles) {
                        if (other == v || other.isTurning()) continue;
                        if (myHitbox.intersects(other.getShrinkHitbox(2))) {
                            blocked = true; break;
                        }
                    }
                    if (blocked) {
                        // Guard fires: revert arc position.
                        v.setX(savedX); v.setY(savedY); v.setTurnT(savedT);
                        // FIX-1: speed=0 makes this vehicle invisible to isTurnSafe →
                        // vehicles queued behind can attempt their own turns without
                        // waiting indefinitely for this one to clear the intersection.
                        v.setSpeed(0);
                        v.incTurnBlockedTicks();
                    } else {
                        v.resetTurnBlockedTicks();
                    }
                } else {
                    v.resetTurnBlockedTicks();
                }
                continue;
            }


            for (Intersection inter : intersections) {
                if (inter.type.equals("5way") || inter.type.equals("4way")) {
                    boolean inApproachZone = (v.getX() > inter.x - 200 && v.getX() < inter.x + roadWidth + 200 &&
                            v.getY() > roadStartY - 200 && v.getY() < roadStartY + roadWidth + 200);
                    if (inApproachZone) {
                        if (v.getTurnIntent().equals("LEFT"))
                            v.setTurnIntent("STRAIGHT");
                        if (inter.type.equals("5way") && v.getTurnIntent().equals("DIAGONAL")
                                && v.getDirection() != Direction.SOUTHWEST)
                            v.setTurnIntent("STRAIGHT");
                    }
                }
            }

            executeTurn(v);
            // ── Root-Cause-3 fix: if executeTurn just committed the vehicle to a Bézier
            //    this very tick, advance the curve immediately and skip linear movement.
            //    Without this, v.move() below would call updatePosition() and add speed
            //    pixels in the OLD direction, causing a one-frame linear overshoot.
            //    Same collision guard as the early-check above.
            if (v.isTurning()) {
                double savedX = v.getX(), savedY = v.getY(), savedT = v.getTurnT();
                boolean done = v.advanceTurn();
                if (!done) {
                    Rectangle myHitbox = v.getHitbox();
                    boolean blocked = false;
                    for (Vehicle other : vehicles) {
                        if (other == v || other.isTurning()) continue;
                        if (myHitbox.intersects(other.getShrinkHitbox(2))) {
                            blocked = true; break;
                        }
                    }
                    if (blocked) {
                        v.setX(savedX); v.setY(savedY); v.setTurnT(savedT);
                        v.setSpeed(0);
                        v.incTurnBlockedTicks();
                    } else {
                        v.resetTurnBlockedTicks();
                    }
                } else {
                    v.resetTurnBlockedTicks();
                }
                continue;
            }
            boolean lightAllows = canVehicleGo(v);

            // Đã xóa khối lệnh snapToStopLine ở đây để chống lỗi xe nhảy cóc đè lên nhau

            boolean clearAheadFar = checkSafeDistance(v, 250);
            if (!clearAheadFar && lightAllows)
                tryOvertake(v);
            else if (clearAheadFar)
                returnToSlowLane(v);
            yieldToEmergency(v);

            // Close-stop threshold: diagonal vehicles need a larger threshold (65px)
            // because
            // their body diagonal is sqrt(55^2+32^2) ≈ 63.5px, vs 35px for straight lanes.
            int closeThreshold = (v.getDirection() == Direction.NORTHEAST
                    || v.getDirection() == Direction.SOUTHWEST) ? 65 : 35;
            if (checkSafeDistance(v, closeThreshold) && !v.isWaitingToTurn()) {
                v.move(lightAllows);
            } else {
                v.setSpeed(0);
            }
        }
        if (ticks % 100 == 0)
            justYielded.clear();
        vehicles.removeIf(v -> v.getX() < -300 || v.getX() > width + 300 || v.getY() < -300 || v.getY() > height + 300);
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    /**
     * Reset all mutable controller state without touching the intersection list.
     * Clears vehicles, resets the tick counter, lane-change cooldowns, and the
     * justYielded set. Useful for DiagnosticRunner headless reruns and for any
     * future scenario where a soft-reset (same intersections, fresh traffic) is
     * preferred over full reinstantiation.
     */
    public void reset() {
        vehicles.clear();
        ticks = 0;
        laneChangeCooldown.clear();
        justYielded.clear();
    }

    // Used by DiagnosticRunner to distinguish red-light stops from obstacle
    // deadlocks.
    public boolean isWaitingForLight(Vehicle v) {
        Intersection approaching = getApproachingIntersection(v);
        if (approaching == null)
            return false;
        return isInStopZone(v, approaching) && !approaching.light.canGo(v.getDirection());
    }
}
