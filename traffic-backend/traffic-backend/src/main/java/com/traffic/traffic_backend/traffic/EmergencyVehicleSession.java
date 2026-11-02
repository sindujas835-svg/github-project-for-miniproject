package com.traffic.traffic_backend.traffic;

/**
 * Tracks an active emergency vehicle session at the intersection.
 * Manages tracking state, temporary detection loss grace period, and clearance confirmation.
 */
public class EmergencyVehicleSession {

    private final String vehicleId;
    private final Road road;
    private final int priority;
    private final long requestTime;

    private long lastDetectedTimestamp;
    private int graceSecondsRemaining;
    private boolean inIntersection;
    private boolean passedIntersection;

    public EmergencyVehicleSession(String vehicleId, Road road, int priority, int gracePeriodSeconds) {
        this.vehicleId = vehicleId;
        this.road = road;
        this.priority = priority;
        this.requestTime = System.currentTimeMillis();
        this.lastDetectedTimestamp = this.requestTime;
        this.graceSecondsRemaining = gracePeriodSeconds;
        this.inIntersection = false;
        this.passedIntersection = false;
    }

    public void recordDetection(int configuredGracePeriod) {
        this.lastDetectedTimestamp = System.currentTimeMillis();
        this.graceSecondsRemaining = configuredGracePeriod;
    }

    public boolean decrementGracePeriod() {
        if (graceSecondsRemaining > 0) {
            graceSecondsRemaining--;
        }
        return graceSecondsRemaining <= 0;
    }

    public String getVehicleId() { return vehicleId; }
    public Road getRoad() { return road; }
    public int getPriority() { return priority; }
    public long getRequestTime() { return requestTime; }
    public long getLastDetectedTimestamp() { return lastDetectedTimestamp; }
    public int getGraceSecondsRemaining() { return graceSecondsRemaining; }
    public boolean isInIntersection() { return inIntersection; }
    public void setInIntersection(boolean inIntersection) { this.inIntersection = inIntersection; }
    public boolean isPassedIntersection() { return passedIntersection; }
    public void setPassedIntersection(boolean passedIntersection) { this.passedIntersection = passedIntersection; }

    @Override
    public String toString() {
        return "EmergencyVehicleSession{" +
                "id='" + vehicleId + '\'' +
                ", road=" + road +
                ", prio=" + priority +
                ", grace=" + graceSecondsRemaining +
                ", passed=" + passedIntersection +
                '}';
    }
}
