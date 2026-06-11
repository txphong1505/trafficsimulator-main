package core.vehicles;

import core.constants.Direction;
import java.awt.Color;

public class Car extends Vehicle {
    public Car(double x, double y, Direction dir) {
        super(x, y, dir, "Car", Color.BLUE, 5);
    }
}