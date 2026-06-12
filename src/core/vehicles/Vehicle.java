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

    protected static final int LENGTH = 55;
    protected static final int WIDTH = 32;
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
        if (r < 50) turnIntent = "STRAIGHT";
        else if (r < 65) turnIntent = "RIGHT";
        else if (r < 80) turnIntent = "LEFT";
        else turnIntent = "DIAGONAL";
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

    public void draw(Graphics2D g2d) {
        if (!graphicMode) {
            int bw = getBodyWidth(), bh = getBodyHeight();
            g2d.setColor(color);
            g2d.fillRect((int) x, (int) y, bw, bh);
            g2d.setColor(color.darker());
            g2d.drawRect((int) x, (int) y, bw, bh);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
            FontMetrics fm = g2d.getFontMetrics();
            int tx = (int) x + (bw - fm.stringWidth(name)) / 2;
            int ty = (int) y + (bh + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(name, tx, ty);
            return;
        }

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
            int tx = (int) x + (bw - fm.stringWidth(name)) / 2;
            int ty = (int) y + (bh + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(name, tx, ty);
            return;
        }

        double angle = 0;
        switch (direction) {
            case EAST:      angle = 0;                break;
            case SOUTH:     angle = Math.PI / 2;      break;
            case WEST:      angle = Math.PI;           break;
            case NORTH:     angle = -Math.PI / 2;      break;
            case NORTHEAST: angle = -Math.PI / 4;      break;
            case SOUTHWEST: angle = 3 * Math.PI / 4;   break;
        }
        angle += Math.PI / 2;

        AffineTransform old = g2d.getTransform();
        double centerX = x + getBodyWidth() / 2.0;
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

    public int getBodyWidth()  { return (direction == Direction.EAST || direction == Direction.WEST) ? LENGTH : WIDTH; }
    public int getBodyHeight() { return (direction == Direction.NORTH || direction == Direction.SOUTH) ? LENGTH : WIDTH; }
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