package core.vehicles;

import core.constants.Direction;
import java.awt.Color;

public class Bicycle extends Vehicle {
    public Bicycle(double x, double y, Direction dir) {
        super(x, y, dir, "Bike", Color.GREEN, 3);
    }
}