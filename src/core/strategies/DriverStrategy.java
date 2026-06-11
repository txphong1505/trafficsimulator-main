package core.strategies;

import core.vehicles.Vehicle;

public interface DriverStrategy {
    void move(Vehicle vehicle, boolean lightAllows);
}