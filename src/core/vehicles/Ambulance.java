package core.vehicles;

import core.constants.Direction;
import java.awt.Color;

public class Ambulance extends Vehicle {
    public Ambulance(double x, double y, Direction dir) {
        super(x, y, dir, "Ambu", Color.WHITE, 7);
    }
}