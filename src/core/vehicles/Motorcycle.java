package core.vehicles;

import core.constants.Direction;
import java.awt.Color;

public class Motorcycle extends Vehicle {
    public Motorcycle(double x, double y, Direction dir) {
        super(x, y, dir, "Motor", Color.RED, 6);
    }
}