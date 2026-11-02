package com.traffic.traffic_backend.traffic;

import java.util.Map;

/**
 * Immutable telemetry snapshot of the intersection traffic signals at a single point in time.
 * Pushed via WebSocket and returned by REST endpoints for real-time dashboard visualization.
 */
public class TrafficStateSnapshot {

    private final long timestamp;
    private final Road activeRoad;
    private final Road nextRoad;
    private final TrafficPhaseType phaseType;
    private final int secondsRemaining;
    private final Map<Road, SignalColor> signals;
    private final boolean emergencyActive;
    private final String emergencyVehicleId;
    private final String statusMessage;
    private final long cycleNumber;
    private final boolean paused;

    public TrafficStateSnapshot(
            long timestamp,
            Road activeRoad,
            Road nextRoad,
            TrafficPhaseType phaseType,
            int secondsRemaining,
            Map<Road, SignalColor> signals,
            boolean emergencyActive,
            String emergencyVehicleId,
            String statusMessage,
            long cycleNumber,
            boolean paused) {
        this.timestamp = timestamp;
        this.activeRoad = activeRoad;
        this.nextRoad = nextRoad;
        this.phaseType = phaseType;
        this.secondsRemaining = secondsRemaining;
        this.signals = signals;
        this.emergencyActive = emergencyActive;
        this.emergencyVehicleId = emergencyVehicleId;
        this.statusMessage = statusMessage;
        this.cycleNumber = cycleNumber;
        this.paused = paused;
    }

    public long getTimestamp() { return timestamp; }
    public Road getActiveRoad() { return activeRoad; }
    public Road getNextRoad() { return nextRoad; }
    public TrafficPhaseType getPhaseType() { return phaseType; }
    public int getSecondsRemaining() { return secondsRemaining; }
    public Map<Road, SignalColor> getSignals() { return signals; }
    public boolean isEmergencyActive() { return emergencyActive; }
    public String getEmergencyVehicleId() { return emergencyVehicleId; }
    public String getStatusMessage() { return statusMessage; }
    public long getCycleNumber() { return cycleNumber; }
    public boolean isPaused() { return paused; }
}
