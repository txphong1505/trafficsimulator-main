package core.environment;

import core.constants.Direction;

public class TrafficLight {
    private boolean isFiveWay;
    private String phase;
    private String state;
    private int countdown;
    private int tick;
    private boolean manualMode;

    public TrafficLight(boolean isFiveWay) {
        this.isFiveWay = isFiveWay;
        this.phase = "HORIZONTAL";
        this.state = "GREEN";
        this.countdown = 8;
        this.tick = 0;
        this.manualMode = false;
    }

    public void update() {
        if (manualMode) return;
        tick++;
        if (tick >= 40) {
            tick = 0;
            countdown--;
            if (countdown <= 0) switchState();
        }
    }

    public void change() {
        if (!manualMode) return;
        switchState();
    }

    private void switchState() {
        if (state.equals("GREEN")) {
            state = "YELLOW";
            countdown = 2;
        } else {
            state = "GREEN";
            if (isFiveWay) {
                switch (phase) {
                    case "HORIZONTAL": phase = "VERTICAL"; countdown = 8; break;
                    case "VERTICAL": phase = "DIAGONAL"; countdown = 6; break;
                    default: phase = "HORIZONTAL"; countdown = 8;
                }
            } else {
                phase = phase.equals("HORIZONTAL") ? "VERTICAL" : "HORIZONTAL";
                countdown = 8;
            }
        }
    }

    public boolean canGo(Direction direction) {
        if (!state.equals("GREEN")) return false;
        if (isFiveWay) {
            if (phase.equals("HORIZONTAL")) return direction == Direction.EAST || direction == Direction.WEST;
            if (phase.equals("VERTICAL")) return direction == Direction.NORTH || direction == Direction.SOUTH;
            if (phase.equals("DIAGONAL")) return direction == Direction.SOUTHWEST;
        } else {
            if (phase.equals("HORIZONTAL")) return direction == Direction.EAST || direction == Direction.WEST;
            if (phase.equals("VERTICAL")) return direction == Direction.NORTH || direction == Direction.SOUTH;
        }
        return false;
    }

    public boolean isYellowFor(Direction direction) {
        if (!state.equals("YELLOW")) return false;
        if (isFiveWay) {
            if (phase.equals("HORIZONTAL")) return direction == Direction.EAST || direction == Direction.WEST;
            if (phase.equals("VERTICAL")) return direction == Direction.NORTH || direction == Direction.SOUTH;
            if (phase.equals("DIAGONAL")) return direction == Direction.SOUTHWEST;
        } else {
            if (phase.equals("HORIZONTAL")) return direction == Direction.EAST || direction == Direction.WEST;
            if (phase.equals("VERTICAL")) return direction == Direction.NORTH || direction == Direction.SOUTH;
        }
        return false;
    }

    public int getRemainingRedTime(Direction direction) {
        String[] phasesOrder = isFiveWay ? new String[]{"HORIZONTAL", "VERTICAL", "DIAGONAL"} : new String[]{"HORIZONTAL", "VERTICAL"};
        String targetPhase;
        if (direction == Direction.EAST || direction == Direction.WEST) targetPhase = "HORIZONTAL";
        else if (direction == Direction.NORTH || direction == Direction.SOUTH) targetPhase = "VERTICAL";
        else targetPhase = "DIAGONAL";

        if (phase.equals(targetPhase)) return 0;

        int currentIdx = indexOf(phasesOrder, phase);
        int targetIdx = indexOf(phasesOrder, targetPhase);

        int timeLeft;
        if (state.equals("GREEN")) timeLeft = countdown + 2;
        else timeLeft = countdown;

        int idx = (currentIdx + 1) % phasesOrder.length;
        while (idx != targetIdx) {
            if (isFiveWay) {
                String p = phasesOrder[idx];
                if (p.equals("HORIZONTAL") || p.equals("VERTICAL")) timeLeft += 10;
                else if (p.equals("DIAGONAL")) timeLeft += 8;
            } else {
                timeLeft += 10;
            }
            idx = (idx + 1) % phasesOrder.length;
        }
        return timeLeft;
    }

    private int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return -1;
    }

    public void setManualMode(boolean mode) { this.manualMode = mode; }
    public String getState() { return state; }
    public int getCountdown() { return countdown; }

    public void fastForward(int seconds) {
        if (!manualMode) return;
        int remaining = seconds;
        while (remaining > 0) {
            int reduce = Math.min(remaining, countdown);
            countdown -= reduce;
            remaining -= reduce;
            if (countdown <= 0) switchState();
        }
    }
}