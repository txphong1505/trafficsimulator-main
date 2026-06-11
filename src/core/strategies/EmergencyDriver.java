package core.strategies;

import core.vehicles.Vehicle;

public class EmergencyDriver implements DriverStrategy {
    @Override
    public void move(Vehicle vehicle, boolean lightAllows) {
        // Xe ưu tiên được vượt đèn đỏ
        vehicle.setSpeed(vehicle.getOriginalSpeed() + 5);
        vehicle.updatePosition();
    }
}