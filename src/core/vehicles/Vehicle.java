package core.vehicles;

import core.constants.Direction;
import core.strategies.DriverStrategy;
import core.audio.SoundManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class Vehicle {
    protected double x, y;
    protected Direction direction;
    protected String name;
    protected Color color;
    protected double speed;
    protected final double originalSpeed;
    protected String turnIntent;
    protected boolean hasTurned;
    protected DriverStrategy driverStrategy;
    protected boolean waitingToTurn = false;

    // ── Smooth-turn Bézier state ────────────────────────────────────────────
    // Position and angle follow a quadratic Bézier B(t) = (1-t)²P0 + 2t(1-t)P1 + t²P2.
    // Physics (hitbox, getBodyWidth/Height) are NEVER modified during a turn.
    private boolean   isTurning   = false;
    private double    turnT       = 0;          // curve parameter [0 .. 1]
    private double    turnP0x, turnP0y;         // start point
    private double    turnP1x, turnP1y;         // control point (corner tangent)
    private double    turnP2x, turnP2y;         // end point (exit lane)
    private double    turnArcLen  = 1;          // estimated arc length for dt calc
    private Direction turnExitDir = Direction.EAST;
    /** Current render angle (rad), same sign convention as draw(). Continuously updated. */
    double turnAngle = 0;
    // ────────────────────────────────────────────────────────────────────────

    protected static final int LENGTH = 55;
    protected static final int WIDTH  = 32;
    protected static final Random random = new Random();
    protected static Map<String, BufferedImage> spriteCache = new HashMap<>();

    public static boolean graphicMode = true;

    public Vehicle(double x, double y, Direction direction, String name, Color color, double speed) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.name = name;
        this.color = color;
        this.speed = speed;
        this.originalSpeed = speed;
        this.hasTurned = false;

        int r = random.nextInt(100);
        if      (r < 50) turnIntent = "STRAIGHT";
        else if (r < 65) turnIntent = "RIGHT";
        else if (r < 80) turnIntent = "LEFT";
        else             turnIntent = "DIAGONAL";
    }

    public void move(boolean isAllowed) {
        if (driverStrategy != null) {
            driverStrategy.move(this, isAllowed);
            if (speed == 0 && !isAllowed && !isWaitingAtTrafficLight()) {
                SoundManager.playHornOnNearCollision();
            }
        } else {
            if (!isAllowed) {
                speed = 0;
                if (!isWaitingAtTrafficLight()) {
                    SoundManager.playHornOnNearCollision();
                }
                return;
            }
            speed = originalSpeed;
            updatePosition();
        }
    }

    private boolean isWaitingAtTrafficLight() {
        return this.waitingToTurn;
    }

    protected void loadSprite(String name) {
        if (!spriteCache.containsKey(name)) {
            try {
                BufferedImage img = ImageIO.read(new File("assets/" + name.toLowerCase() + ".png"));
                spriteCache.put(name, img);
            } catch (IOException e) { }
        }
    }

    /**
     * Draw this vehicle.
     *
     * @param g2d              the Graphics2D context (already scaled by Main's global transform)
     * @param intersectionBoxes pre-computed intersection bounding boxes (inter.x, roadStartY,
     *                          roadWidth, roadWidth); used in BASIC mode to trigger visual scale.
     *                          May be null or empty — treated as "no intersection".
     */
    public void draw(Graphics2D g2d, List<Rectangle> intersectionBoxes) {
        if (!graphicMode) {
            // ── BASIC MODE ───────────────────────────────────────────────────────────
            // Feature 1: scale up visually when inside an intersection.
            // Smooth-turn: rotate the rectangle to face the direction of travel.
            // Physics (x, y, getBodyWidth/Height, hitboxes) are NEVER modified.
            boolean inIntersection = false;
            if (intersectionBoxes != null) {
                for (Rectangle box : intersectionBoxes) {
                    if (box.intersects(getHitbox())) { inIntersection = true; break; }
                }
            }

            int bw = getBodyWidth(), bh = getBodyHeight();
            double vcx = x + bw / 2.0, vcy = y + bh / 2.0;

            AffineTransform old = g2d.getTransform();
            if (inIntersection || isTurning) {
                // Apply scale and/or rotation around vehicle centre in a single transform.
                g2d.translate(vcx, vcy);
                if (inIntersection) g2d.scale(1.35, 1.35);
                if (isTurning)      g2d.rotate(turnAngle - Math.PI / 2.0);
                // Now draw centred at origin in the transformed space.
                g2d.setColor(color);
                g2d.fillRect(-bw / 2, -bh / 2, bw, bh);
                g2d.setColor(color.darker());
                g2d.drawRect(-bw / 2, -bh / 2, bw, bh);
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString(name,
                        -fm.stringWidth(name) / 2,
                        (fm.getAscent() - fm.getDescent()) / 2);
            } else {
                // Fast path for non-turning, non-intersection vehicles — unchanged from before.
                g2d.setColor(color);
                g2d.fillRect((int) x, (int) y, bw, bh);
                g2d.setColor(color.darker());
                g2d.drawRect((int) x, (int) y, bw, bh);
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
                FontMetrics fm = g2d.getFontMetrics();
                int tx = (int) x + (bw - fm.stringWidth(name)) / 2;
                int ty = (int) y + (bh + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(name, tx, ty);
            }
            g2d.setTransform(old); // always restore
            return;
        }

        // ── GRAPHIC MODE ─────────────────────────────────────────────────────────
        loadSprite(name);
        BufferedImage sprite = spriteCache.get(name);
        int imgW = 32, imgH = 55;
        if (name.equals("Motor") || name.equals("Bike")) { imgW = 16; imgH = 40; }
        else if (name.equals("Ambu") || name.equals("Fire")) { imgW = 34; imgH = 60; }

        if (sprite == null) {
            int bw = getBodyWidth(), bh = getBodyHeight();
            g2d.setColor(color);
            g2d.fillRect((int) x, (int) y, bw, bh);
            g2d.setColor(color.darker());
            g2d.drawRect((int) x, (int) y, bw, bh);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
            FontMetrics fm = g2d.getFontMetrics();
            int tx = (int) x + (getBodyWidth() - fm.stringWidth(name)) / 2;
            int ty = (int) y + (getBodyHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(name, tx, ty);
            return;
        }

        // Compute render angle — use continuous Bézier angle when turning,
        // otherwise derive from the discrete direction enum.
        double angle;
        if (isTurning) {
            angle = turnAngle;   // already has the +PI/2 sprite-orientation offset
        } else {
            switch (direction) {
                case EAST:      angle = 0;               break;
                case SOUTH:     angle = Math.PI / 2;     break;
                case WEST:      angle = Math.PI;          break;
                case NORTH:     angle = -Math.PI / 2;     break;
                case NORTHEAST: angle = -Math.PI / 4;     break;
                case SOUTHWEST: angle = 3 * Math.PI / 4;  break;
                default:        angle = 0;               break;
            }
            angle += Math.PI / 2;
        }

        AffineTransform old = g2d.getTransform();
        double centerX = x + getBodyWidth()  / 2.0;
        double centerY = y + getBodyHeight() / 2.0;
        g2d.translate(centerX, centerY);
        g2d.rotate(angle);
        g2d.drawImage(sprite, -imgW / 2, -imgH / 2, imgW, imgH, null);
        g2d.setTransform(old);
    }

    public void updatePosition() {
        double diag = Math.sqrt(2) / 2;
        switch (direction) {
            case EAST:      x += speed;           break;
            case WEST:      x -= speed;           break;
            case SOUTH:     y += speed;           break;
            case NORTH:     y -= speed;           break;
            case NORTHEAST: x += speed * diag; y -= speed * diag; break;
            case SOUTHWEST: x -= speed * diag; y += speed * diag; break;
        }
    }

    // ── Smooth-turn Bézier API ───────────────────────────────────────────────

    /**
     * Begin a quadratic Bézier turn: path goes from P0 through control point P1
     * to exit point P2, then the vehicle continues in exitDir.
     * <p>
     * The vehicle is snapped to P0 immediately.  All movement during the turn is
     * handled by repeated calls to {@link #advanceTurn()} in the simulation loop
     * — NOT through {@link #updatePosition()}.
     */
    public void beginTurn(double p0x, double p0y,
                          double p1x, double p1y,
                          double p2x, double p2y,
                          Direction exitDir) {
        isTurning   = true;
        turnT       = 0;
        turnP0x = p0x; turnP0y = p0y;
        turnP1x = p1x; turnP1y = p1y;
        turnP2x = p2x; turnP2y = p2y;
        turnExitDir = exitDir;
        turnArcLen  = bezierArcLen(p0x, p0y, p1x, p1y, p2x, p2y);
        if (turnArcLen < 1) turnArcLen = 1;
        // Snap vehicle to start of arc.
        x = p0x; y = p0y;
        // Initial angle: tangent direction at t=0 → 2*(P1-P0); fall back to P0→P2.
        double dx = 2 * (p1x - p0x), dy = 2 * (p1y - p0y);
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) { dx = p2x - p0x; dy = p2y - p0y; }
        turnAngle = Math.atan2(dy, dx) + Math.PI / 2;
    }

    /**
     * Advance the Bézier curve by one simulation tick at {@link #originalSpeed}.
     *
     * @return {@code true} when the turn has completed.
     */
    public boolean advanceTurn() {
        if (!isTurning) return true;
        turnT = Math.min(1.0, turnT + originalSpeed / turnArcLen);
        double t = turnT, u = 1 - t;
        // Position on the quadratic Bézier.
        x = u*u*turnP0x + 2*u*t*turnP1x + t*t*turnP2x;
        y = u*u*turnP0y + 2*u*t*turnP1y + t*t*turnP2y;
        // Tangent direction → continuous render angle.
        double dx = 2*u*(turnP1x - turnP0x) + 2*t*(turnP2x - turnP1x);
        double dy = 2*u*(turnP1y - turnP0y) + 2*t*(turnP2y - turnP1y);
        if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5)
            turnAngle = Math.atan2(dy, dx) + Math.PI / 2;
        // Finalise when curve is complete.
        if (turnT >= 1.0) {
            isTurning = false;
            direction = turnExitDir;
            hasTurned = true;
            x = turnP2x; y = turnP2y;
            return true;
        }
        return false;
    }

    public boolean isTurning() { return isTurning; }

    /** 16-segment numerical arc-length estimate for a quadratic Bézier. */
    private static double bezierArcLen(double p0x, double p0y,
                                        double p1x, double p1y,
                                        double p2x, double p2y) {
        int N = 16;
        double len = 0, px = p0x, py = p0y;
        for (int i = 1; i <= N; i++) {
            double t = i / (double) N, u = 1 - t;
            double nx = u*u*p0x + 2*u*t*p1x + t*t*p2x;
            double ny = u*u*p0y + 2*u*t*p1y + t*t*p2y;
            len += Math.hypot(nx - px, ny - py);
            px = nx; py = ny;
        }
        return len;
    }
    // ────────────────────────────────────────────────────────────────────────

    // ======================== TÍNH ĐÓNG GÓI OOP (ENCAPSULATION) ========================
    public Rectangle getHitbox() {
        return new Rectangle((int)x, (int)y, getBodyWidth(), getBodyHeight());
    }

    public Rectangle getShrinkHitbox(int shrink) {
        return new Rectangle((int)x + shrink, (int)y + shrink, getBodyWidth() - 2*shrink, getBodyHeight() - 2*shrink);
    }

    public Rectangle getPredictedHitbox(int threshold) {
        int cw = getBodyWidth(), ch = getBodyHeight();
        int offset = 6;
        switch (direction) {
            case EAST:      return new Rectangle((int)x, (int)y + offset, cw + threshold, ch - 2*offset);
            case WEST:      return new Rectangle((int)x - threshold, (int)y + offset, cw + threshold, ch - 2*offset);
            case SOUTH:     return new Rectangle((int)x + offset, (int)y, cw - 2*offset, ch + threshold);
            case NORTH:     return new Rectangle((int)x + offset, (int)y - threshold, cw - 2*offset, ch + threshold);
            case NORTHEAST: return new Rectangle((int)x + offset, (int)y - threshold, cw + threshold, ch + threshold);
            case SOUTHWEST: return new Rectangle((int)x - threshold, (int)y + offset, cw + threshold, ch + threshold);
            default:        return getHitbox();
        }
    }

    public Rectangle getBlindSpot(int behind, int ahead, int scan, int offset) {
        if (turnIntent.equals("STRAIGHT")) return null;
        int length = Math.max(getBodyWidth(), getBodyHeight()) + behind + ahead;
        int vx = (int) x, vy = (int) y, vw = getBodyWidth(), vh = getBodyHeight();

        if (direction == Direction.EAST) {
            if (turnIntent.equals("RIGHT")) return new Rectangle(vx - behind, vy + vh + offset, length, scan);
            else if (turnIntent.equals("LEFT"))  return new Rectangle(vx - behind, vy - scan - offset, length, scan);
        } else if (direction == Direction.WEST) {
            if (turnIntent.equals("RIGHT")) return new Rectangle(vx - ahead, vy - scan - offset, length, scan);
            else if (turnIntent.equals("LEFT"))  return new Rectangle(vx - ahead, vy + vh + offset, length, scan);
        } else if (direction == Direction.SOUTH) {
            if (turnIntent.equals("RIGHT")) return new Rectangle(vx - scan - offset, vy - behind, scan, length);
            else if (turnIntent.equals("LEFT"))  return new Rectangle(vx + vw + offset, vy - behind, scan, length);
        } else if (direction == Direction.NORTH) {
            if (turnIntent.equals("RIGHT")) return new Rectangle(vx + vw + offset, vy - ahead, scan, length);
            else if (turnIntent.equals("LEFT"))  return new Rectangle(vx - scan - offset, vy - ahead, scan, length);
        }
        return null;
    }
    // ====================================================================================

    public int getBodyWidth()  {
        if (direction == Direction.NORTHEAST || direction == Direction.SOUTHWEST) return LENGTH;
        return (direction == Direction.EAST || direction == Direction.WEST) ? LENGTH : WIDTH;
    }
    public int getBodyHeight() {
        if (direction == Direction.NORTHEAST || direction == Direction.SOUTHWEST) return WIDTH;
        return (direction == Direction.NORTH || direction == Direction.SOUTH) ? LENGTH : WIDTH;
    }
    public double getX()       { return x; }
    public double getY()       { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public Direction getDirection()              { return direction; }
    public void setDirection(Direction d)        { this.direction = d; }
    public String getName()                      { return name; }
    public double getSpeed()                     { return speed; }
    public void setSpeed(double s)               { this.speed = s; }
    public double getOriginalSpeed()             { return originalSpeed; }
    public String getTurnIntent()                { return turnIntent; }
    public boolean hasTurned()                   { return hasTurned; }
    public void setHasTurned(boolean t)          { hasTurned = t; }
    public void setWaitingToTurn(boolean waiting){ this.waitingToTurn = waiting; }
    public boolean isWaitingToTurn()             { return waitingToTurn; }
    public void setDriverStrategy(DriverStrategy strategy) { this.driverStrategy = strategy; }
    public void setTurnIntent(String intent)     { this.turnIntent = intent; }
}