package core.environment;

public class Intersection {
    public int x, y;
    public String type; // "3way", "4way", "5way"
    public TrafficLight light;

    public Intersection(int x, int y, String type, TrafficLight light) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.light = light;
    }
}