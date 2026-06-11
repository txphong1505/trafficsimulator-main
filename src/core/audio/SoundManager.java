package core.audio;

import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static final Map<String, Clip> loopingSirens = new HashMap<>();
    private static long lastHornTime = 0;

    // Quản lý âm lượng toàn cầu (0.0 đến 1.0)
    public static float volume = 0.7f;

    public static void setVolume(float newVolume) {
        volume = newVolume;
        if (volume < 0.0f) volume = 0.0f;
        if (volume > 1.0f) volume = 1.0f;
        updateLoopVolumes();
    }

    private static void applyVolume(Clip clip) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (volume <= 0.0f) ? gainControl.getMinimum() : (float) (Math.log10(volume) * 20.0f);
                gainControl.setValue(db);
            }
        } catch (Exception e) {}
    }

    private static void updateLoopVolumes() {
        for (Clip clip : loopingSirens.values()) {
            if (clip != null && clip.isRunning()) {
                applyVolume(clip);
            }
        }
    }

    public static void updateEmergencySiren(String type, String filePath, boolean shouldPlay) {
        try {
            if (shouldPlay) {
                if (!loopingSirens.containsKey(type) || !loopingSirens.get(type).isRunning()) {
                    File soundFile = new File(filePath);
                    if (soundFile.exists()) {
                        AudioInputStream audioInput = AudioSystem.getAudioInputStream(soundFile);
                        Clip clip = AudioSystem.getClip();
                        clip.open(audioInput);
                        applyVolume(clip);
                        clip.loop(Clip.LOOP_CONTINUOUSLY);
                        clip.start();
                        loopingSirens.put(type, clip);
                    }
                }
            } else {
                if (loopingSirens.containsKey(type)) {
                    Clip clip = loopingSirens.get(type);
                    if (clip != null && clip.isRunning()) {
                        clip.stop();
                        clip.close();
                    }
                    loopingSirens.remove(type);
                }
            }
        } catch (Exception e) {}
    }

    // --- LOGIC CÒI TỰ NHIÊN MỚI ---
    public static synchronized void playHornOnNearCollision() {
        long now = System.currentTimeMillis();

        // 1. CHỐNG SPAM: Tuyệt đối không cho tiếng còi nào kêu cách nhau dưới 2.5 giây
        if (now - lastHornTime < 2500) return;

        // 2. TẠO SỰ TỰ NHIÊN: Khi kẹt đường, xe sẽ bóp còi ngẫu nhiên thay vì kêu liên thanh
        if (Math.random() < 0.05) {
            lastHornTime = now;
            playOneShot("src/resources/sounds/horn.wav");
        }
    }

    private static void playOneShot(String filePath) {
        new Thread(() -> {
            try {
                File soundFile = new File(filePath);
                if (soundFile.exists()) {
                    AudioInputStream audioInput = AudioSystem.getAudioInputStream(soundFile);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioInput);
                    applyVolume(clip);
                    clip.start();
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) clip.close();
                    });
                }
            } catch (Exception e) {}
        }).start();
    }
}