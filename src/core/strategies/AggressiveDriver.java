package core.strategies;

import core.vehicles.Vehicle;

public class AggressiveDriver implements DriverStrategy {
    @Override
    public void move(Vehicle vehicle, boolean lightAllows) {
        if (!lightAllows) {
            vehicle.setSpeed(0);
            return;
        }
        vehicle.setSpeed(vehicle.getOriginalSpeed() + 4);
        vehicle.updatePosition();
    }
}