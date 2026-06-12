package core;

import core.controller.TrafficController;
import core.environment.Intersection;
import core.environment.TrafficLight;
import core.renderer.Renderer;
import core.vehicles.Vehicle;
import core.audio.SoundManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class Main extends JPanel implements ActionListener {
    // ── Kích thước gốc làm chuẩn thiết kế ──────────────────────────────────────
    private static final int BASE_WIDTH  = 1600;
    private static final int BASE_HEIGHT = 900;

    // Shared road geometry constants (match TrafficController & Renderer)
    private static final int ROAD_START_Y = 220;
    private static final int ROAD_WIDTH   = 240;

    // ── Chế độ bản đồ (Map Mode) ─────────────────────────────────────────────
    private enum MapMode { NGA_BA, NGA_TU, NGA_NAM, TONG_HOP }
    private MapMode currentMapMode = MapMode.TONG_HOP;

    private List<Intersection> intersections;
    private List<Vehicle>      vehicles;
    private TrafficController  controller;
    private Renderer           renderer;
    private Timer              timer;
    private boolean manualMode  = false;
    private boolean highTraffic = false;

    // ── Nút bấm UI ───────────────────────────────────────────────────────────
    private Rectangle modeButtonRect;
    private Rectangle trafficButtonRect;
    private Rectangle renderModeButtonRect;
    private Rectangle mapSelectRect;   // overall hitbox of the 2×2 map-mode panel

    // ── Âm lượng ────────────────────────────────────────────────────────────
    private int currentVolume = 70;

    // ============================================================
    public Main() {
        setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
        setBackground(new Color(85, 170, 85));
        setLayout(null);

        vehicles     = new ArrayList<>();
        intersections = new ArrayList<>();

        // Default: TONG_HOP — all three intersections
        intersections.add(new Intersection(150,  200, "3way", new TrafficLight(false)));
        intersections.add(new Intersection(600,  200, "4way", new TrafficLight(false)));
        intersections.add(new Intersection(1200, 200, "5way", new TrafficLight(true)));

        controller = new TrafficController(intersections, vehicles);
        renderer   = new Renderer(BASE_WIDTH, BASE_HEIGHT);

        // Button rectangles (base coordinates, painted inside the global scale transform)
        modeButtonRect       = new Rectangle(20,  20, 160, 45);
        trafficButtonRect    = new Rectangle(200, 20, 160, 45);
        renderModeButtonRect = new Rectangle(380, 20, 170, 45);
        // Map selection panel sits directly to the right of the volume panel (x=560+240+20=820)
        mapSelectRect        = new Rectangle(820, 10, 240, 75);

        // ── Mouse handling ───────────────────────────────────────────────────
        MouseAdapter ma = new MouseAdapter() {
            /** Convert raw event coords to base-resolution canvas coords. */
            private Point toCanvas(MouseEvent e) {
                double scaleX = (double) getWidth()  / BASE_WIDTH;
                double scaleY = (double) getHeight() / BASE_HEIGHT;
                double scale  = Math.min(scaleX, scaleY);
                int offsetX   = (getWidth()  - (int)(BASE_WIDTH  * scale)) / 2;
                int offsetY   = (getHeight() - (int)(BASE_HEIGHT * scale)) / 2;
                return new Point(
                    (int)((e.getX() - offsetX) / scale),
                    (int)((e.getY() - offsetY) / scale)
                );
            }

            private void handleVol(MouseEvent e) {
                Point p = toCanvas(e);
                int mx = p.x, my = p.y;
                // Hitbox for volume slider knob area
                if (mx >= 650 && mx <= 790 && my >= 20 && my <= 60) {
                    int newVol = (int)(((mx - 660) / 120.0) * 100);
                    currentVolume = Math.max(0, Math.min(100, newVol));
                    SoundManager.setVolume(currentVolume / 100f);
                    repaint();
                }
            }

            @Override public void mousePressed(MouseEvent e)  { handleVol(e); }
            @Override public void mouseDragged(MouseEvent e)  { handleVol(e); }

            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = toCanvas(e);
                int mx = p.x, my = p.y;

                if (modeButtonRect.contains(mx, my)) {
                    manualMode = !manualMode;
                    for (Intersection inter : intersections)
                        inter.light.setManualMode(manualMode);
                    repaint();
                    return;
                }

                if (trafficButtonRect.contains(mx, my)) {
                    highTraffic = !highTraffic;
                    repaint();
                    return;
                }

                if (renderModeButtonRect.contains(mx, my)) {
                    Vehicle.graphicMode = !Vehicle.graphicMode;
                    repaint();
                    return;
                }

                // ── Map selection panel click ────────────────────────────────
                if (mapSelectRect.contains(mx, my)) {
                    MapMode clicked = resolveMapClick(mx, my);
                    if (clicked != currentMapMode) {
                        currentMapMode = clicked;
                        switchMapMode(currentMapMode);
                    }
                    repaint();
                    return;
                }

                // Manual-mode light advance (click anywhere else)
                if (manualMode) {
                    for (Intersection inter : intersections)
                        inter.light.fastForward(4);
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        timer = new Timer(25, this);
        timer.start();

        for (int i = 0; i < 10; i++)
            controller.spawnVehicle();
    }

    /**
     * Map a click inside mapSelectRect to the corresponding MapMode.
     * The panel is a 2×2 grid:
     *   [ ngã ba  | ngã tư   ]
     *   [ ngã năm | tổng hợp ]
     */
    private MapMode resolveMapClick(int mx, int my) {
        int midX = mapSelectRect.x + mapSelectRect.width  / 2;  // 940
        int midY = mapSelectRect.y + mapSelectRect.height / 2;  // 47
        if (mx < midX) {
            return (my < midY) ? MapMode.NGA_BA  : MapMode.NGA_NAM;
        } else {
            return (my < midY) ? MapMode.NGA_TU  : MapMode.TONG_HOP;
        }
    }

    /**
     * Full simulation reset + environment rebuild for the chosen map mode.
     * Reinstantiating TrafficLight resets phase/state/countdown.
     * Reinstantiating TrafficController resets ticks, cooldowns, and justYielded.
     */
    private void switchMapMode(MapMode mode) {
        // 1. Remove all active vehicles
        vehicles.clear();

        // 2. Force tree layout regeneration for the new intersection positions
        renderer.resetTrees();

        // 2. Rebuild intersection list (fresh TrafficLight = automatic phase reset)
        intersections.clear();
        switch (mode) {
            case NGA_BA:
                // Centre the 3-way at x=600 (same column as the default 4-way)
                // so all coordinate-dependent systems compute against the screen centre.
                intersections.add(new Intersection(600, 200, "3way", new TrafficLight(false)));
                break;
            case NGA_TU:
                intersections.add(new Intersection(600,  200, "4way", new TrafficLight(false)));
                break;
            case NGA_NAM:
                // Centre the 5-way at x=600 for the same reason.
                intersections.add(new Intersection(600, 200, "5way", new TrafficLight(true)));
                break;
            case TONG_HOP:
            default:
                intersections.add(new Intersection(150,  200, "3way", new TrafficLight(false)));
                intersections.add(new Intersection(600,  200, "4way", new TrafficLight(false)));
                intersections.add(new Intersection(1200, 200, "5way", new TrafficLight(true)));
                break;
        }

        // 3. Rebuild controller (resets ticks, laneChangeCooldown, justYielded)
        controller = new TrafficController(intersections, vehicles);

        // 4. Re-propagate the current manual-mode flag to new lights
        for (Intersection inter : intersections)
            inter.light.setManualMode(manualMode);

        // 5. Spawn initial batch of vehicles for the new environment
        for (int i = 0; i < 8; i++)
            controller.spawnVehicle();
    }

    /**
     * Pre-compute intersection bounding boxes for BASIC-mode visual scaling.
     * Each box is (inter.x, ROAD_START_Y, ROAD_WIDTH, ROAD_WIDTH) — the same
     * rectangle used by DiagnosticRunner and Renderer for intersection geometry.
     */
    private List<Rectangle> getIntersectionBoxes() {
        List<Rectangle> boxes = new ArrayList<>(intersections.size());
        for (Intersection inter : intersections) {
            boxes.add(new Rectangle(inter.x, ROAD_START_Y, ROAD_WIDTH, ROAD_WIDTH));
        }
        return boxes;
    }

    // ── Simulation loop ──────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        for (Intersection inter : intersections)
            inter.light.update();

        double spawnRate = highTraffic ? 0.06 : 0.03;
        if (Math.random() < spawnRate)
            controller.spawnVehicle();
        controller.updateAll();

        boolean ambulancePresent = false, firetruckPresent = false;
        for (Vehicle v : vehicles) {
            if (v.getName().equals("Ambu")) ambulancePresent = true;
            if (v.getName().equals("Fire")) firetruckPresent = true;
        }
        SoundManager.updateEmergencySiren("Ambu", "src/resources/sounds/ambulance.wav", ambulancePresent);
        SoundManager.updateEmergencySiren("Fire", "src/resources/sounds/firetruck.wav", firetruckPresent);
        repaint();
    }

    // ── Rendering ────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Pixel-art rendering hints (no anti-aliasing, nearest-neighbour scale)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Global responsive scale + letterbox offset
        double scaleX = (double) getWidth()  / BASE_WIDTH;
        double scaleY = (double) getHeight() / BASE_HEIGHT;
        double scale  = Math.min(scaleX, scaleY);
        int offsetX   = (getWidth()  - (int)(BASE_WIDTH  * scale)) / 2;
        int offsetY   = (getHeight() - (int)(BASE_HEIGHT * scale)) / 2;
        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);

        // Environment
        renderer.drawBackgroundAndRoads(g2d, intersections);
        renderer.drawTrafficLights(g2d, intersections);

        // Vehicles — pass intersection boxes so BASIC mode can apply visual scale
        List<Rectangle> intersectionBoxes = getIntersectionBoxes();
        for (Vehicle v : vehicles) {
            v.draw(g2d, intersectionBoxes);
        }

        // ── Volume panel ─────────────────────────────────────────────────────
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(560, 20, 240, 45, 12, 12);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2d.drawString("Vol: " + currentVolume + "%", 575, 47);

        int volX = 660, volY = 38, volW = 120, volH = 8;
        g2d.setColor(new Color(50, 50, 50));
        g2d.fillRoundRect(volX, volY, volW, volH, 4, 4);
        int fillW = (int)(volW * (currentVolume / 100.0));
        g2d.setColor(new Color(52, 152, 219));
        g2d.fillRoundRect(volX, volY, fillW, volH, 4, 4);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(volX + fillW - 6, volY - 3, 14, 14);

        // ── Map selection panel ───────────────────────────────────────────────
        drawMapSelectionPanel(g2d);

        // ── Control buttons ───────────────────────────────────────────────────
        drawStyledButton(g2d, modeButtonRect,
                "Mode: " + (manualMode ? "MANUAL" : "AUTO"),
                manualMode ? new Color(231, 76, 60) : new Color(46, 204, 113));
        drawStyledButton(g2d, trafficButtonRect,
                "Traffic: " + (highTraffic ? "HIGH" : "LOW"),
                highTraffic ? new Color(230, 126, 34) : new Color(52, 152, 219));
        drawStyledButton(g2d, renderModeButtonRect,
                "Render: " + (Vehicle.graphicMode ? "GRAPHIC" : "BASIC"),
                Vehicle.graphicMode ? new Color(142, 68, 173) : new Color(127, 140, 141));
    }

    /**
     * Draw the 2×2 map-mode selection panel.
     * Layout (base coords):
     *   [ ngã ba  (top-left)  | ngã tư   (top-right)  ]
     *   [ ngã năm (bot-left)  | tổng hợp (bot-right)  ]
     * Semi-transparent black background matching the volume panel.
     * Radio-button circles: filled white circle with dark dot = selected;
     * grey outline circle = unselected.
     */
    private void drawMapSelectionPanel(Graphics2D g2d) {
        int px = mapSelectRect.x;  // 820
        int py = mapSelectRect.y;  // 10
        int pw = mapSelectRect.width;   // 240
        int ph = mapSelectRect.height;  // 75

        // Panel background
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(px, py, pw, ph, 12, 12);
        g2d.setColor(new Color(80, 80, 80));
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawRoundRect(px, py, pw, ph, 12, 12);

        // Grid cell layout
        String[]  labels = { "ngã ba",  "ngã tư",   "ngã năm", "tổng hợp" };
        MapMode[] modes  = { MapMode.NGA_BA, MapMode.NGA_TU, MapMode.NGA_NAM, MapMode.TONG_HOP };

        // Two columns, two rows
        int[] colX = { px + 14, px + pw / 2 + 4 };  // { 834, 944 }
        int[] rowY = { py + 28, py + 58 };            // { 38, 68 }

        g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2d.setStroke(new BasicStroke(1.5f));

        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int ix  = colX[col];
            int iy  = rowY[row];
            boolean selected = (modes[i] == currentMapMode);

            // Radio circle icon (12×12)
            if (selected) {
                // Solid white ring + dark inner dot (selected)
                g2d.setColor(Color.WHITE);
                g2d.fillOval(ix, iy - 6, 12, 12);
                g2d.setColor(new Color(20, 20, 20));
                g2d.fillOval(ix + 3, iy - 3, 6, 6);
            } else {
                // Grey-filled circle with darker centre (unselected)
                g2d.setColor(new Color(160, 160, 160));
                g2d.fillOval(ix, iy - 6, 12, 12);
                g2d.setColor(new Color(40, 40, 40));
                g2d.fillOval(ix + 2, iy - 4, 8, 8);
            }

            // Label text
            g2d.setColor(Color.WHITE);
            g2d.drawString(labels[i], ix + 16, iy + 4);
        }
    }

    private void drawStyledButton(Graphics2D g2d, Rectangle rect, String text, Color statusColor) {
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.fillRoundRect(rect.x + 3, rect.y + 3, rect.width, rect.height, 12, 12);
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2d.setColor(statusColor);
        g2d.fillOval(rect.x + 15, rect.y + 17, 12, 12);
        g2d.setColor(new Color(44, 62, 80));
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2d.drawString(text, rect.x + 35, rect.y + 28);
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        JFrame frame = new JFrame("Smart City Traffic Simulation");
        Main panel = new Main();
        frame.add(panel);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        if (screenSize.width < BASE_WIDTH || screenSize.height < BASE_HEIGHT) {
            frame.setSize(screenSize.width - 60, screenSize.height - 100);
        } else {
            frame.pack();
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setVisible(true);
    }
}