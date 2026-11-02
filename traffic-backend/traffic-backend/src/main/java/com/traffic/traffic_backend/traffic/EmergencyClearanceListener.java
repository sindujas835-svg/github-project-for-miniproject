package com.traffic.traffic_backend.traffic;

/**
 * Listener notified when an active emergency vehicle priority clearance completes.
 */
@FunctionalInterface
public interface EmergencyClearanceListener {
    void onEmergencyCompleted(String vehicleId, Road road);
}
