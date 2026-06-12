package core.renderer;

import core.constants.Direction;
import core.environment.Intersection;
import core.environment.TrafficLight; // Phục vụ vẽ đèn

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Renderer {
    private int width, height;
    private int roadWidth = 240;
    private int roadStartY = 220;

    private TexturePaint grassTexture;
    private TexturePaint roadTexture;
    private TexturePaint curbTexture;
    private BufferedImage treeSprite;

    private List<Point> treePositions = new ArrayList<>();
    private boolean treesGenerated = false;

    public Renderer(int width, int height) {
        this.width = width;
        this.height = height;
        generatePixelTiles();
    }

    private void generatePixelTiles() {
        int TILE_SIZE = 32;
        Random rand = new Random(12345);

        // 1. TẠO GẠCH CỎ
        BufferedImage grassImg = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grassImg.createGraphics();
        g.setColor(new Color(85, 170, 85));
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(65, 150, 65));
        for(int i=0; i<15; i++) g.fillRect(rand.nextInt(TILE_SIZE), rand.nextInt(TILE_SIZE), 2, 2);
        g.setColor(new Color(105, 190, 105));
        for(int i=0; i<10; i++) g.fillRect(rand.nextInt(TILE_SIZE), rand.nextInt(TILE_SIZE), 2, 2);
        g.setColor(new Color(220, 220, 80));
        for(int i=0; i<2; i++) g.fillRect(rand.nextInt(TILE_SIZE), rand.nextInt(TILE_SIZE), 2, 2);
        g.dispose();
        grassTexture = new TexturePaint(grassImg, new Rectangle(0, 0, TILE_SIZE, TILE_SIZE));

        // 2. TẠO GẠCH ĐƯỜNG NHỰA
        BufferedImage roadImg = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        g = roadImg.createGraphics();
        g.setColor(new Color(65, 70, 75));
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(55, 60, 65));
        for(int i=0; i<25; i++) g.fillRect(rand.nextInt(TILE_SIZE), rand.nextInt(TILE_SIZE), 2, 2);
        g.dispose();
        roadTexture = new TexturePaint(roadImg, new Rectangle(0, 0, TILE_SIZE, TILE_SIZE));

        // 3. TẠO GẠCH VỈA HÈ
        BufferedImage curbImg = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        g = curbImg.createGraphics();
        g.setColor(new Color(190, 190, 190));
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(140, 140, 140));
        g.drawRect(0, 0, TILE_SIZE-1, TILE_SIZE-1);
        g.dispose();
        curbTexture = new TexturePaint(curbImg, new Rectangle(0, 0, TILE_SIZE, TILE_SIZE));

        // 4. TẠO SPRITE CÂY
        treeSprite = new BufferedImage(48, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = treeSprite.createGraphics();
        gt.setColor(new Color(90, 60, 40));
        gt.fillRect(20, 40, 8, 24);
        gt.setColor(new Color(110, 80, 50));
        gt.fillRect(20, 40, 3, 24);
        gt.setColor(new Color(30, 90, 30));
        gt.fillRect(8, 16, 32, 28);
        gt.fillRect(4, 20, 40, 20);
        gt.fillRect(16, 8, 16, 36);
        gt.setColor(new Color(45, 115, 40));
        gt.fillRect(12, 12, 24, 24);
        gt.fillRect(8, 16, 32, 16);
        gt.setColor(new Color(60, 140, 50));
        gt.fillRect(16, 8, 16, 16);
        gt.fillRect(12, 12, 8, 8);
        gt.fillRect(28, 16, 8, 8);
        gt.setColor(new Color(20, 70, 20));
        gt.fillRect(32, 28, 8, 8);
        gt.fillRect(12, 32, 8, 4);
        gt.dispose();
    }

    private void generateTrees(List<Intersection> intersections) {
        Random rand = new Random(42);
        int curb = 40;

        for (int i = 0; i < 400; i++) {
            int tx = rand.nextInt(width - 48);
            int ty = rand.nextInt(height - 64);
            boolean isValid = true;

            if (ty + 64 > roadStartY - curb && ty < roadStartY + roadWidth + curb) isValid = false;

            for (Intersection inter : intersections) {
                if (tx + 48 > inter.x - curb && tx < inter.x + roadWidth + curb) {
                    if (inter.type.equals("3way")) {
                        if (ty + 64 > roadStartY - curb) isValid = false;
                    } else {
                        isValid = false;
                    }
                }
            }

            // Dynamic exclusion for the diagonal road NE of any 5-way intersection.
            // The road centre-line satisfies x+y = cx5+cy5 (slope −1, NE direction).
            // Perpendicular distance from tree (tx,ty) to that line = |tx+ty−(cx5+cy5)| / √2.
            // Block trees within (roadWidth/2 + curb) of the centre-line that are
            // on the NE side (tx+ty > cx5+cy5−roadWidth) and above the horizontal road.
            for (Intersection diagInter : intersections) {
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
                    if (Math.hypot(p.x - tx, p.y - ty) < 45) {
                        isValid = false; break;
                    }
                }
            }
            if (isValid) treePositions.add(new Point(tx, ty));
        }
    }

    /** Invalidate the cached tree layout. Call before switching to a different intersection
     *  configuration so that {@code generateTrees()} runs again on the next draw with
     *  the new intersection list, producing correct tree-free road-column zones. */
    public void resetTrees() {
        treesGenerated = false;
        treePositions.clear();
    }

    private void drawFillet(Graphics2D g2d, int cx, int cy, int quadrant) {
        int R = 35, C = 6;
        Shape oldClip = g2d.getClip();
        if (quadrant == 1) {
            g2d.setClip(new Rectangle(cx, cy - R, R, R));
            g2d.setPaint(roadTexture); g2d.fillRect(cx, cy - R, R, R);
            g2d.setPaint(curbTexture); g2d.fillOval(cx + R - R, cy - R - R, R*2, R*2);
            g2d.setPaint(grassTexture); g2d.fillOval(cx + R - (R-C), cy - R - (R-C), (R-C)*2, (R-C)*2);
        } else if (quadrant == 2) {
            g2d.setClip(new Rectangle(cx - R, cy - R, R, R));
            g2d.setPaint(roadTexture); g2d.fillRect(cx - R, cy - R, R, R);
            g2d.setPaint(curbTexture); g2d.fillOval(cx - R - R, cy - R - R, R*2, R*2);
            g2d.setPaint(grassTexture); g2d.fillOval(cx - R - (R-C), cy - R - (R-C), (R-C)*2, (R-C)*2);
        } else if (quadrant == 3) {
            g2d.setClip(new Rectangle(cx - R, cy, R, R));
            g2d.setPaint(roadTexture); g2d.fillRect(cx - R, cy, R, R);
            g2d.setPaint(curbTexture); g2d.fillOval(cx - R - R, cy + R - R, R*2, R*2);
            g2d.setPaint(grassTexture); g2d.fillOval(cx - R - (R-C), cy + R - (R-C), (R-C)*2, (R-C)*2);
        } else if (quadrant == 4) {
            g2d.setClip(new Rectangle(cx, cy, R, R));
            g2d.setPaint(roadTexture); g2d.fillRect(cx, cy, R, R);
            g2d.setPaint(curbTexture); g2d.fillOval(cx + R - R, cy + R - R, R*2, R*2);
            g2d.setPaint(grassTexture); g2d.fillOval(cx + R - (R-C), cy + R - (R-C), (R-C)*2, (R-C)*2);
        }
        g2d.setClip(oldClip);
    }

    public void drawBackgroundAndRoads(Graphics2D g2d, List<Intersection> intersections) {
        if (!treesGenerated) {
            generateTrees(intersections);
            treesGenerated = true;
        }

        int EX = 1500;
        g2d.setPaint(grassTexture);
        g2d.fillRect(-EX, -EX, width + EX*2, height + EX*2);

        int curb = 6;
        g2d.setPaint(curbTexture);
        g2d.fillRect(-EX, roadStartY - curb, width + EX*2, roadWidth + curb*2);
        for (Intersection inter : intersections) {
            if (inter.type.equals("3way")) {
                g2d.fillRect(inter.x - curb, roadStartY, roadWidth + curb*2, height - roadStartY + EX);
            } else if (inter.type.equals("4way") || inter.type.equals("5way")) {
                g2d.fillRect(inter.x - curb, -EX, roadWidth + curb*2, height + EX*2);
            }
        }

        Intersection inter5 = intersections.stream().filter(i -> i.type.equals("5way")).findFirst().orElse(null);
        if (inter5 != null) {
            int cx5 = inter5.x + roadWidth/2;
            int cy5 = roadStartY + roadWidth/2;
            int length = 3000;
            int endX = cx5 + length, endY = cy5 - length;
            double curbHw = (roadWidth/2.0) + curb;
            double dxc = curbHw * 0.707, dyc = curbHw * 0.707;
            Polygon curbPoly = new Polygon();
            curbPoly.addPoint((int)(cx5 - dxc), (int)(cy5 - dyc));
            curbPoly.addPoint((int)(cx5 + dxc), (int)(cy5 + dyc));
            curbPoly.addPoint((int)(endX + dxc), (int)(endY + dyc));
            curbPoly.addPoint((int)(endX - dxc), (int)(endY - dyc));
            g2d.fillPolygon(curbPoly);
        }

        g2d.setPaint(roadTexture);
        g2d.fillRect(-EX, roadStartY, width + EX*2, roadWidth);
        for (Intersection inter : intersections) {
            if (inter.type.equals("3way")) {
                g2d.fillRect(inter.x, roadStartY, roadWidth, height - roadStartY + EX);
            } else if (inter.type.equals("4way") || inter.type.equals("5way")) {
                g2d.fillRect(inter.x, -EX, roadWidth, height + EX*2);
            }
        }
        if (inter5 != null) {
            int cx5 = inter5.x + roadWidth/2;
            int cy5 = roadStartY + roadWidth/2;
            int length = 3000;
            int endX = cx5 + length, endY = cy5 - length;
            double roadHw = roadWidth/2.0;
            double dxr = roadHw * 0.707, dyr = roadHw * 0.707;
            Polygon roadPoly = new Polygon();
            roadPoly.addPoint((int)(cx5 - dxr), (int)(cy5 - dyr));
            roadPoly.addPoint((int)(cx5 + dxr), (int)(cy5 + dyr));
            roadPoly.addPoint((int)(endX + dxr), (int)(endY + dyr));
            roadPoly.addPoint((int)(endX - dxr), (int)(endY - dyr));
            g2d.fillPolygon(roadPoly);
        }

        for (Intersection inter : intersections) {
            drawFillet(g2d, inter.x, roadStartY + roadWidth, 3);
            drawFillet(g2d, inter.x + roadWidth, roadStartY + roadWidth, 4);
            if (inter.type.equals("4way")) {
                drawFillet(g2d, inter.x, roadStartY, 2);
                drawFillet(g2d, inter.x + roadWidth, roadStartY, 1);
            } else if (inter.type.equals("5way")) {
                drawFillet(g2d, inter.x, roadStartY, 2);
            }
        }

        for (Point p : treePositions) {
            g2d.setColor(new Color(0, 0, 0, 40));
            g2d.fillOval(p.x + 8, p.y + 54, 32, 12);
            g2d.drawImage(treeSprite, p.x, p.y, null);
        }

        g2d.setColor(new Color(240, 240, 240));
        int laneMidY = roadStartY + roadWidth/2;
        int segStartX = -EX;
        for (Intersection inter : intersections) {
            int segEndX = inter.x - 45;
            if (segEndX > segStartX) {
                for (int x = segStartX; x < segEndX; x += 40) {
                    if (x + 20 <= segEndX) g2d.fillRect(x, laneMidY - 2, 20, 4);
                }
            }
            segStartX = inter.x + roadWidth + 45;
        }
        if (segStartX < width + EX) {
            for (int x = segStartX; x < width + EX; x += 40) {
                g2d.fillRect(x, laneMidY - 2, 20, 4);
            }
        }
        for (Intersection inter : intersections) {
            int laneMidX = inter.x + roadWidth/2;
            int startY = roadStartY + roadWidth + 45;
            for (int y = startY; y < height + EX; y += 40) {
                if (y + 20 <= height + EX) g2d.fillRect(laneMidX - 2, y, 4, 20);
            }
            if (inter.type.equals("4way") || inter.type.equals("5way")) {
                int endY = roadStartY - 45;
                for (int y = -EX; y < endY; y += 40) {
                    if (y + 20 <= endY) g2d.fillRect(laneMidX - 2, y, 4, 20);
                }
            }
        }

        if (inter5 != null) {
            g2d.setStroke(new BasicStroke(4));
            int cx5 = inter5.x + roadWidth/2;
            int cy5 = roadStartY + roadWidth/2;
            double currentDist = roadWidth/2.0 + 45;
            double maxDist = 3000;
            while (currentDist < maxDist) {
                int dashStartX = (int)(cx5 + currentDist * 0.707);
                int dashStartY = (int)(cy5 - currentDist * 0.707);
                int dashEndX = (int)(cx5 + (currentDist + 20) * 0.707);
                int dashEndY = (int)(cy5 - (currentDist + 20) * 0.707);
                g2d.drawLine(dashStartX, dashStartY, dashEndX, dashEndY);
                currentDist += 40;
            }
            g2d.setStroke(new BasicStroke(1));
        }

        for (Intersection inter : intersections) {
            for (int off = 10; off < roadWidth - 10; off += 25) {
                g2d.fillRect(inter.x + off, roadStartY + roadWidth + 10, 14, 30);
            }
            if (inter.type.equals("4way") || inter.type.equals("5way")) {
                for (int off = 10; off < roadWidth - 10; off += 25) {
                    g2d.fillRect(inter.x + off, roadStartY - 40, 14, 30);
                }
            }
            for (int off = 10; off < roadWidth - 10; off += 25) {
                g2d.fillRect(inter.x - 40, roadStartY + off, 30, 14);
                g2d.fillRect(inter.x + roadWidth + 10, roadStartY + off, 30, 14);
            }
        }
    }

    public void drawTrafficLights(Graphics2D g2d, List<Intersection> intersections) {
        for (Intersection inter : intersections) {
            java.util.List<Direction> dirs = new java.util.ArrayList<>();
            dirs.add(Direction.EAST);

            // Đã trả lại đèn WEST cho ngã 5
            dirs.add(Direction.WEST);

            if (inter.type.equals("3way") || inter.type.equals("4way") || inter.type.equals("5way")) dirs.add(Direction.SOUTH);
            if (inter.type.equals("4way") || inter.type.equals("5way")) dirs.add(Direction.NORTH);
            if (inter.type.equals("5way")) dirs.add(Direction.SOUTHWEST);

            for (Direction dir : dirs) {
                int px = 0, py = 0;
                switch (dir) {
                    case EAST: px = inter.x - 50; py = roadStartY + roadWidth + 25; break;
                    case WEST:
                        if (inter.type.equals("5way")) {
                            px = inter.x + roadWidth + 90;
                            py = roadStartY - 70;
                        } else {
                            px = inter.x + roadWidth + 20; py = roadStartY - 110;
                        }
                        break;
                    case SOUTH: px = inter.x - 50; py = roadStartY - 110; break;
                    case NORTH: px = inter.x + roadWidth + 20; py = roadStartY + roadWidth + 25; break;
                    case SOUTHWEST:
                        px = inter.x + roadWidth + 20;
                        py = roadStartY - 110;
                        break;
                    default:
                        break;
                }
                drawTrafficLightPole(g2d, inter.light, px, py, dir, inter.type);
            }
        }
    }

    // ======================== MÔ HÌNH MVC MỚI ========================
    // Lớp Renderer hoàn toàn chịu trách nhiệm đồ họa (View)
    private void drawTrafficLightPole(Graphics2D g2d, TrafficLight light, int x, int y, Direction dir, String intersectionType) {
        boolean isGreen = light.canGo(dir);
        boolean isYellow = light.isYellowFor(dir);
        boolean isRed = !isGreen && !isYellow;

        g2d.setColor(new Color(105, 110, 115));
        g2d.fillRect(x + 6, y + 42, 6, 24);
        g2d.setColor(new Color(75, 80, 85));
        g2d.fillRect(x + 10, y + 42, 2, 24);

        g2d.setColor(new Color(35, 40, 45));
        g2d.fillRect(x, y, 18, 42);
        g2d.setColor(new Color(70, 75, 80));
        g2d.drawRect(x, y, 17, 41);

        Color offRed = new Color(60, 15, 15);
        Color offYellow = new Color(60, 60, 15);
        Color offGreen = new Color(15, 60, 15);

        Color onRed = new Color(255, 50, 50);
        Color onYellow = new Color(255, 230, 0);
        Color onGreen = new Color(50, 255, 50);

        g2d.setColor(isRed ? onRed : offRed);
        g2d.fillOval(x + 4, y + 4, 10, 10);

        g2d.setColor(isYellow ? onYellow : offYellow);
        g2d.fillOval(x + 4, y + 16, 10, 10);

        g2d.setColor(isGreen ? onGreen : offGreen);
        g2d.fillOval(x + 4, y + 28, 10, 10);

        int displayVal = 0;
        Color textColor = Color.WHITE;

        if (isRed) {
            displayVal = light.getRemainingRedTime(dir);
            textColor = onRed;
        } else if (isYellow) {
            displayVal = light.getCountdown();
            textColor = onYellow;
        } else {
            displayVal = light.getCountdown();
            textColor = onGreen;
        }

        boolean shouldDrawText = true;
        if (intersectionType.equals("3way")) {
            shouldDrawText = false;
        } else if (intersectionType.equals("4way")) {
            if (displayVal > 3) shouldDrawText = false;
        }

        if (shouldDrawText) {
            String displayNumber = String.valueOf(displayVal);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
            FontMetrics fm = g2d.getFontMetrics();

            int textX = x + (18 - fm.stringWidth(displayNumber)) / 2;
            int textY = y - 4;

            g2d.setColor(Color.BLACK);
            g2d.drawString(displayNumber, textX + 1, textY + 1);

            g2d.setColor(textColor);
            g2d.drawString(displayNumber, textX, textY);
        }
    }
}