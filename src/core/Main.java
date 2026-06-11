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
    // Kích thước gốc làm chuẩn thiết kế
    private static final int BASE_WIDTH = 1600;
    private static final int BASE_HEIGHT = 900;

    private List<Intersection> intersections;
    private List<Vehicle> vehicles;
    private TrafficController controller;
    private Renderer renderer;
    private Timer timer;
    private boolean manualMode = false;
    private boolean highTraffic = false;

    private Rectangle modeButtonRect;
    private Rectangle trafficButtonRect;

    // ======================== BIẾN LƯU TRỮ ÂM LƯỢNG (THAY THẾ JSLIDER) ========================
    private int currentVolume = 70;
    // =========================================================================================

    public Main() {
        // Mặc định ban đầu, vẫn ưu tiên kích thước chuẩn
        setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
        setBackground(new Color(85, 170, 85)); // Màu cỏ chuẩn của bạn
        setLayout(null);

        vehicles = new ArrayList<>();
        intersections = new ArrayList<>();

        TrafficLight light3 = new TrafficLight(false);
        TrafficLight light4 = new TrafficLight(false);
        TrafficLight light5 = new TrafficLight(true);
        intersections.add(new Intersection(150, 200, "3way", light3));
        intersections.add(new Intersection(600, 200, "4way", light4));
        intersections.add(new Intersection(1200, 200, "5way", light5));

        controller = new TrafficController(intersections, vehicles);
        renderer = new Renderer(BASE_WIDTH, BASE_HEIGHT);

        // Khởi tạo tọa độ nút bấm theo kích thước gốc chuẩn
        modeButtonRect = new Rectangle(20, 20, 160, 45);
        trafficButtonRect = new Rectangle(200, 20, 160, 45);

        // ======================== XỬ LÝ SỰ KIỆN CLICK CHUỘT & KÉO THANH ÂM LƯỢNG ========================
        MouseAdapter ma = new MouseAdapter() {
            private void handleVol(MouseEvent e) {
                double scaleX = (double) getWidth() / BASE_WIDTH;
                double scaleY = (double) getHeight() / BASE_HEIGHT;
                double scale = Math.min(scaleX, scaleY);
                int offsetX = (getWidth() - (int) (BASE_WIDTH * scale)) / 2;
                int offsetY = (getHeight() - (int) (BASE_HEIGHT * scale)) / 2;
                int mx = (int) ((e.getX() - offsetX) / scale);
                int my = (int) ((e.getY() - offsetY) / scale);

                // Hitbox khớp chính xác 100% với hình vẽ thanh Volume (Tọa độ X từ 480 -> 600, Rộng 120px)
                if (mx >= 470 && mx <= 610 && my >= 20 && my <= 60) {
                    int newVol = (int) (((mx - 480) / 120.0) * 100);
                    currentVolume = Math.max(0, Math.min(100, newVol));
                    SoundManager.setVolume(currentVolume / 100f);
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) { handleVol(e); }

            @Override
            public void mouseDragged(MouseEvent e) { handleVol(e); }

            @Override
            public void mouseClicked(MouseEvent e) {
                double scaleX = (double) getWidth() / BASE_WIDTH;
                double scaleY = (double) getHeight() / BASE_HEIGHT;
                double scale = Math.min(scaleX, scaleY);
                int offsetX = (getWidth() - (int) (BASE_WIDTH * scale)) / 2;
                int offsetY = (getHeight() - (int) (BASE_HEIGHT * scale)) / 2;
                int mx = (int) ((e.getX() - offsetX) / scale);
                int my = (int) ((e.getY() - offsetY) / scale);

                if (modeButtonRect.contains(mx, my)) {
                    manualMode = !manualMode;
                    for (Intersection inter : intersections) {
                        inter.light.setManualMode(manualMode);
                    }
                    repaint();
                    return;
                }

                if (trafficButtonRect.contains(mx, my)) {
                    highTraffic = !highTraffic;
                    repaint();
                    return;
                }

                if (manualMode) {
                    for (Intersection inter : intersections) {
                        inter.light.fastForward(4);
                    }
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        // ==============================================================================================

        timer = new Timer(25, this);
        timer.start();

        for (int i = 0; i < 10; i++) controller.spawnVehicle();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Intersection inter : intersections) {
            inter.light.update();
        }
        double spawnRate = highTraffic ? 0.06 : 0.03;
        if (Math.random() < spawnRate) {
            controller.spawnVehicle();
        }
        controller.updateAll();

        boolean ambulancePresent = false;
        boolean firetruckPresent = false;
        for (Vehicle v : vehicles) {
            if (v.getName().equals("Ambu")) ambulancePresent = true;
            if (v.getName().equals("Fire")) firetruckPresent = true;
        }
        SoundManager.updateEmergencySiren("Ambu", "src/resources/sounds/ambulance.wav", ambulancePresent);
        SoundManager.updateEmergencySiren("Fire", "src/resources/sounds/firetruck.wav", firetruckPresent);

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // BỘ LỌC ĐỒ HỌA PIXEL ART: Tắt toàn bộ tính năng làm mờ mịn
        // 1. Tắt làm mờ/nhòe khi phóng to ảnh xe cộ (Ép phóng to theo kiểu cục vuông)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // 2. Tắt khử răng cưa cho các khối hình học (mặt đường, vạch kẻ sẽ vuông thành sắc cạnh)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // 3. Tắt khử răng cưa cho chữ số (số đếm ngược trên đèn sẽ ra đúng chất pixel)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        double scaleX = (double) getWidth() / BASE_WIDTH;
        double scaleY = (double) getHeight() / BASE_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        int offsetX = (getWidth() - (int) (BASE_WIDTH * scale)) / 2;
        int offsetY = (getHeight() - (int) (BASE_HEIGHT * scale)) / 2;

        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);

        // VẼ MẶT ĐƯỜNG VÀ CỎ BẰNG CODE (Gọi từ Renderer)
        renderer.drawBackgroundAndRoads(g2d, intersections);

        // VẼ ĐÈN GIAO THÔNG
        renderer.drawTrafficLights(g2d, intersections);

        // VẼ XE
        for (Vehicle v : vehicles) {
            v.draw(g2d);
        }

        // ======================== VẼ THANH VOLUME & GIAO DIỆN UI ========================
        // Vẽ nền UI Panel Volume
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(380, 20, 240, 45, 12, 12);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2d.drawString("Vol: " + currentVolume + "%", 395, 47);

        // Vẽ thanh Volume Bar tự chế (Pixel-perfect)
        int volX = 480;
        int volY = 38;
        int volW = 120; // Rộng chuẩn 120px
        int volH = 8;

        g2d.setColor(new Color(50, 50, 50));
        g2d.fillRoundRect(volX, volY, volW, volH, 4, 4); // Rãnh đen

        int fillW = (int) (volW * (currentVolume / 100.0));
        g2d.setColor(new Color(52, 152, 219));
        g2d.fillRoundRect(volX, volY, fillW, volH, 4, 4); // Rãnh xanh

        g2d.setColor(Color.WHITE);
        g2d.fillOval(volX + fillW - 6, volY - 3, 14, 14); // Nút tròn
        // ================================================================================

        drawStyledButton(g2d, modeButtonRect, "Mode: " + (manualMode ? "MANUAL" : "AUTO"),
                manualMode ? new Color(231, 76, 60) : new Color(46, 204, 113));

        drawStyledButton(g2d, trafficButtonRect, "Traffic: " + (highTraffic ? "HIGH" : "LOW"),
                highTraffic ? new Color(230, 126, 34) : new Color(52, 152, 219));
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