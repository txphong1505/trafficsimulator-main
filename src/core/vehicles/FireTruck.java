package core.vehicles;

import core.constants.Direction;
import java.awt.Color;

public class FireTruck extends Vehicle {
    public FireTruck(double x, double y, Direction dir) {
        super(x, y, dir, "Fire", new Color(255, 165, 0), 7);
    }
}