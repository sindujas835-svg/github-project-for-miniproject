package com.traffic.traffic_backend.traffic;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TrafficSignalController: Non-blocking, thread-safe state machine governing both
 * the Normal 4-Way Traffic Cycle and the Safe Emergency Vehicle Preemption Protocol.
 *
 * SAFETY INVARIANTS:
 * 1. NEVER activate conflicting green signals under any circumstances.
 * 2. NEVER switch directly from one road's green to another road's green.
 * 3. Conflicting active greens must transition through YELLOW and ALL-RED before preemption.
 * 4. Post-emergency restoration must flush through ALL-RED before normal cycle resumes.
 */
@Service
public class TrafficSignalController {

    private static final Logger log = LoggerFactory.getLogger(TrafficSignalController.class);

    private final TrafficConfig config;
    private final TrafficSafetyGuard safetyGuard;
    private final SimpMessagingTemplate messagingTemplate;

    private final ReentrantLock stateLock = new ReentrantLock();
    private ScheduledExecutorService scheduler;

    // Normal state machine variables
    private Road activeRoad = Road.NORTH;
    private Road nextRoad = Road.SOUTH;
    private Road interruptedRoad = Road.NORTH;
    private TrafficPhaseType phaseType = TrafficPhaseType.ALL_RED_IDLE;
    private int secondsRemaining = 0;
    private final Map<Road, SignalColor> signals = new EnumMap<>(Road.class);

    // Emergency preemption variables
    private boolean emergencyActive = false;
    private EmergencyVehicleSession activeSession = null;
    private EmergencyClearanceListener clearanceListener = null;
    private boolean paused = false;
    private long cycleNumber = 1;

    public TrafficSignalController(
            TrafficConfig config,
            TrafficSafetyGuard safetyGuard,
            SimpMessagingTemplate messagingTemplate) {
        this.config = config;
        this.safetyGuard = safetyGuard;
        this.messagingTemplate = messagingTemplate;

        // Initialize ALL signals to RED (Initial Idle State)
        for (Road road : Road.values()) {
            signals.put(road, SignalColor.RED);
        }
        this.phaseType = TrafficPhaseType.ALL_RED_IDLE;
        this.secondsRemaining = 0;
    }

    @PostConstruct
    public void start() {
        log.info("Starting TrafficSignalController engine. Initial state: ALL-RED IDLE (Awaiting verification).");
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "traffic-state-machine-tick");
            t.setDaemon(true);
            return t;
        });

        // Ticks every 1000 milliseconds
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down TrafficSignalController scheduler.");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Ticking function executed once every second by the scheduled executor.
     */
    public void tick() {
        stateLock.lock();
        try {
            if (paused) {
                broadcastState("Simulation paused");
                return;
            }

            // In ALL-RED IDLE state, intersection remains all-red until verified emergency preemption
            if (phaseType == TrafficPhaseType.ALL_RED_IDLE) {
                for (Road road : Road.values()) {
                    signals.put(road, SignalColor.RED);
                }
                safetyGuard.validateSignals(signals, phaseType);
                broadcastState(generateStatusMessage());
                return;
            }

            secondsRemaining--;

            if (secondsRemaining <= 0) {
                transitionToNextPhase();
            } else if (emergencyActive && phaseType == TrafficPhaseType.EMERGENCY_GREEN) {
                // Monitor active emergency tracking and grace period
                handleEmergencyGreenTick();
            }

            // Enforce safety invariants at every second tick
            safetyGuard.validateSignals(signals, phaseType);

            broadcastState(generateStatusMessage());
        } catch (Exception e) {
            log.error("Exception in traffic state machine tick: {}. Failing safe to ALL-RED.", e.getMessage(), e);
            failSafeAllRed();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Executes phase transitions for both normal cycles and emergency preemption.
     */
    private void transitionToNextPhase() {
        switch (phaseType) {
            // ==================== NORMAL TRAFFIC CYCLE ====================
            case GREEN_PHASE -> {
                phaseType = TrafficPhaseType.YELLOW_PHASE;
                signals.put(activeRoad, SignalColor.YELLOW);
                secondsRemaining = config.getYellowIntervalSeconds();
                log.info("Cycle {}: Transitioning {} to YELLOW ({}s)", cycleNumber, activeRoad, secondsRemaining);
            }
            case YELLOW_PHASE -> {
                phaseType = TrafficPhaseType.ALL_RED_CLEARANCE;
                signals.put(activeRoad, SignalColor.RED);
                secondsRemaining = config.getAllRedClearanceSeconds();
                log.info("Cycle {}: All-Red clearance interval started ({}s)", cycleNumber, secondsRemaining);
            }
            case ALL_RED_CLEARANCE -> {
                activeRoad = nextRoad;
                nextRoad = activeRoad.next();

                if (activeRoad == Road.NORTH) {
                    cycleNumber++;
                }

                phaseType = TrafficPhaseType.GREEN_PHASE;
                signals.put(activeRoad, SignalColor.GREEN);
                secondsRemaining = config.getGreenIntervalSeconds();
                log.info("Cycle {}: Transitioning {} to GREEN ({}s)", cycleNumber, activeRoad, secondsRemaining);
            }

            // ==================== EMERGENCY PREEMPTION PROTOCOL ====================
            case EMERGENCY_YELLOW -> {
                // Conflicting road has finished its yellow stopping window. Transition to All-Red Pre-Clearance.
                phaseType = TrafficPhaseType.EMERGENCY_PRE_CLEARANCE;
                signals.put(interruptedRoad, SignalColor.RED);
                secondsRemaining = config.getEmergencyPreClearanceSeconds();
                log.info("Emergency Preemption: Flushing intersection with All-Red clearance ({}s)", secondsRemaining);
            }
            case EMERGENCY_PRE_CLEARANCE -> {
                // Intersection is completely clear. Grant GREEN to emergency vehicle's road.
                phaseType = TrafficPhaseType.EMERGENCY_GREEN;
                Road targetRoad = activeSession != null ? activeSession.getRoad() : activeRoad;
                activeRoad = targetRoad;

                // Ensure all 4 roads red, then target road GREEN
                for (Road r : Road.values()) {
                    signals.put(r, SignalColor.RED);
                }
                signals.put(activeRoad, SignalColor.GREEN);
                secondsRemaining = config.getEmergencyMaxGreenSeconds();
                log.info("EMERGENCY PRIORITY GRANTED: {} is GREEN for vehicle {}",
                        activeRoad, activeSession != null ? activeSession.getVehicleId() : "AMBULANCE");
            }
            case EMERGENCY_GREEN -> {
                // Max green window elapsed (safety timeout to prevent gridlock if vehicle stalled)
                log.warn("Emergency max green window expired for {}. Transitioning to post-clearance.",
                        activeSession != null ? activeSession.getVehicleId() : "AMBULANCE");
                initiateEmergencyPostClearance();
            }
            case EMERGENCY_POST_CLEARANCE -> {
                // All-red post-clearance finished safely. Restore normal traffic cycle.
                completeEmergencyClearanceAndResume();
            }
        }
    }

    /**
     * Checks tracking and handles temporary detection loss grace period during EMERGENCY_GREEN.
     */
    private void handleEmergencyGreenTick() {
        if (activeSession == null) return;

        // If ambulance was explicitly confirmed passed
        if (activeSession.isPassedIntersection()) {
            log.info("Ambulance {} confirmed passed. Ending priority green.", activeSession.getVehicleId());
            initiateEmergencyPostClearance();
            return;
        }

        // Countdown grace period when detection is absent for extended duration
        long elapsedSinceDetection = System.currentTimeMillis() - activeSession.getLastDetectedTimestamp();
        if (elapsedSinceDetection > 15000) {
            boolean graceExpired = activeSession.decrementGracePeriod();
            log.warn("Tracking absent for {}. Grace period remaining: {}s",
                    activeSession.getVehicleId(), activeSession.getGraceSecondsRemaining());

            if (graceExpired) {
                log.info("Grace period elapsed for {}. Assuming ambulance has cleared intersection.",
                        activeSession.getVehicleId());
                initiateEmergencyPostClearance();
            }
        }
    }

    /**
     * Transitions from emergency priority into All-Red post-clearance before resuming normal flow.
     */
    private void initiateEmergencyPostClearance() {
        phaseType = TrafficPhaseType.EMERGENCY_POST_CLEARANCE;
        for (Road road : Road.values()) {
            signals.put(road, SignalColor.RED);
        }
        secondsRemaining = config.getEmergencyPostClearanceSeconds();
        log.info("Emergency post-clearance interval started ({}s). All signals RED.", secondsRemaining);
    }

    /**
     * Completes emergency post-clearance safely and returns intersection to ALL-RED IDLE mode.
     * Automatic traffic cycling is completely disabled.
     */
    private void completeEmergencyClearanceAndResume() {
        log.info("Emergency clearance completed. All roads return to RED. System idle.");
        String clearedVehicleId = activeSession != null ? activeSession.getVehicleId() : null;
        Road clearedRoad = activeSession != null ? activeSession.getRoad() : null;

        this.emergencyActive = false;
        this.activeSession = null;

        // Return to ALL-RED IDLE state
        phaseType = TrafficPhaseType.ALL_RED_IDLE;
        for (Road road : Road.values()) {
            signals.put(road, SignalColor.RED);
        }
        secondsRemaining = 0;

        log.info("System returned to ALL-RED IDLE mode (Awaiting next verification).");

        // Notify queue service if more ambulances are waiting
        if (clearanceListener != null && clearedVehicleId != null) {
            try {
                clearanceListener.onEmergencyCompleted(clearedVehicleId, clearedRoad);
            } catch (Exception e) {
                log.error("Error notifying emergency clearance listener: {}", e.getMessage());
            }
        }
    }

    public void setClearanceListener(EmergencyClearanceListener clearanceListener) {
        this.clearanceListener = clearanceListener;
    }

    /**
     * Request emergency preemption (called by EmergencyVerificationService).
     *
     * @param road Approach road of the emergency vehicle.
     * @param vehicleId Unique identifier of the ambulance.
     * @param priority Medical priority (1 = Critical, 2 = High, 3 = Normal).
     * @return true if preemption sequence accepted and activated.
     */
    public boolean requestEmergencyPreemption(Road road, String vehicleId, int priority) {
        stateLock.lock();
        try {
            // Avoid duplicate preemptions for the same vehicle
            if (emergencyActive && activeSession != null && activeSession.getVehicleId().equalsIgnoreCase(vehicleId)) {
                log.info("Emergency preemption already active for vehicle {}. Refreshing detection.", vehicleId);
                activeSession.recordDetection(config.getEmergencyGracePeriodSeconds());
                return true;
            }

            log.warn("EMERGENCY PREEMPTION REQUEST: Vehicle {} on approach road {} (Priority {})",
                    vehicleId, road, priority);

            this.emergencyActive = true;
            this.activeSession = new EmergencyVehicleSession(vehicleId, road, priority, config.getEmergencyGracePeriodSeconds());

            // Case 1: Target road is ALREADY GREEN
            if (activeRoad == road && (phaseType == TrafficPhaseType.GREEN_PHASE || phaseType == TrafficPhaseType.EMERGENCY_GREEN)) {
                log.info("Target road {} is already GREEN. Extending green for emergency preemption.", road);
                this.phaseType = TrafficPhaseType.EMERGENCY_GREEN;
                this.secondsRemaining = config.getEmergencyMaxGreenSeconds();
                broadcastState("Ambulance approaching on active green road. Green extended.");
                return true;
            }

            // Case 2: Intersection is currently in ALL_RED_IDLE state
            if (phaseType == TrafficPhaseType.ALL_RED_IDLE) {
                this.interruptedRoad = activeRoad;
                this.activeRoad = road;
                this.phaseType = TrafficPhaseType.EMERGENCY_GREEN;
                for (Road r : Road.values()) {
                    signals.put(r, SignalColor.RED);
                }
                signals.put(road, SignalColor.GREEN);
                this.secondsRemaining = config.getEmergencyMaxGreenSeconds();
                log.info("Preemption activated from ALL_RED_IDLE: Target road {} is GREEN ({}s)", road, secondsRemaining);
                broadcastState("Dual verification confirmed on " + road + "! Emergency GREEN activated.");
                return true;
            }

            // Case 3: Another road is currently GREEN. Must transition conflicting road safely to YELLOW first!
            if (signals.get(activeRoad) == SignalColor.GREEN) {
                this.interruptedRoad = activeRoad;
                this.phaseType = TrafficPhaseType.EMERGENCY_YELLOW;
                signals.put(activeRoad, SignalColor.YELLOW);
                this.secondsRemaining = config.getEmergencyYellowSeconds();
                log.info("Transitioning conflicting road {} safely to YELLOW ({}s)", activeRoad, secondsRemaining);
                broadcastState("Emergency detected on " + road + ". Safely slowing conflicting road " + interruptedRoad);
                return true;
            }

            // Case 4: Conflicting road is already in YELLOW phase. Let yellow finish, then route into emergency pre-clearance.
            if (signals.get(activeRoad) == SignalColor.YELLOW) {
                this.interruptedRoad = activeRoad;
                this.phaseType = TrafficPhaseType.EMERGENCY_YELLOW;
                log.info("Conflicting road {} already YELLOW. Allowing safe stop to complete.", activeRoad);
                return true;
            }

            // Case 5: Intersection is in ALL_RED_CLEARANCE. Transition into emergency pre-clearance.
            this.phaseType = TrafficPhaseType.EMERGENCY_PRE_CLEARANCE;
            for (Road r : Road.values()) {
                signals.put(r, SignalColor.RED);
            }
            this.secondsRemaining = config.getEmergencyPreClearanceSeconds();
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Update live vehicle tracking telemetry (camera frame feedback).
     */
    public void updateVehicleTracking(String vehicleId, boolean detectedInZone) {
        stateLock.lock();
        try {
            if (activeSession != null && activeSession.getVehicleId().equalsIgnoreCase(vehicleId)) {
                if (detectedInZone) {
                    activeSession.recordDetection(config.getEmergencyGracePeriodSeconds());
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Notify that the ambulance has crossed the intersection exit line.
     */
    public void notifyAmbulancePassed(String vehicleId) {
        stateLock.lock();
        try {
            if (activeSession != null && activeSession.getVehicleId().equalsIgnoreCase(vehicleId)) {
                activeSession.setPassedIntersection(true);
                log.info("Exit sensor confirmed vehicle {} cleared intersection.", vehicleId);
                if (phaseType == TrafficPhaseType.EMERGENCY_GREEN) {
                    initiateEmergencyPostClearance();
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Forcibly release emergency preemption and initiate safe post-clearance.
     */
    public void releaseEmergencyPreemption() {
        stateLock.lock();
        try {
            if (!emergencyActive) return;
            log.info("Manual emergency release invoked for {}.", activeSession != null ? activeSession.getVehicleId() : "Active");
            initiateEmergencyPostClearance();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Fail-safe trip that forces all signals to RED immediately upon safety discrepancy.
     */
    public void failSafeAllRed() {
        for (Road road : Road.values()) {
            signals.put(road, SignalColor.RED);
        }
        phaseType = TrafficPhaseType.ALL_RED_CLEARANCE;
        secondsRemaining = config.getAllRedClearanceSeconds();
        emergencyActive = false;
        activeSession = null;
        broadcastState("EMERGENCY FAIL-SAFE TRIP: All signals forced to RED");
    }

    public void pause() {
        stateLock.lock();
        try {
            this.paused = true;
            log.info("Traffic signal controller PAUSED.");
        } finally {
            stateLock.unlock();
        }
    }

    public void resume() {
        stateLock.lock();
        try {
            this.paused = false;
            log.info("Traffic signal controller RESUMED.");
        } finally {
            stateLock.unlock();
        }
    }

    public void resetToNormal() {
        resetToIdle();
    }

    public void resetToIdle() {
        stateLock.lock();
        try {
            emergencyActive = false;
            activeSession = null;
            paused = false;
            activeRoad = Road.NORTH;
            nextRoad = Road.SOUTH;
            interruptedRoad = Road.NORTH;
            phaseType = TrafficPhaseType.ALL_RED_IDLE;
            secondsRemaining = 0;
            for (Road road : Road.values()) {
                signals.put(road, SignalColor.RED);
            }
            log.info("Reset traffic signal controller to ALL-RED IDLE.");
            broadcastState("Traffic controller reset to ALL-RED IDLE");
        } finally {
            stateLock.unlock();
        }
    }

    public TrafficStateSnapshot getSnapshot() {
        stateLock.lock();
        try {
            Map<Road, SignalColor> copy = new EnumMap<>(signals);
            return new TrafficStateSnapshot(
                    System.currentTimeMillis(),
                    activeRoad,
                    nextRoad,
                    phaseType,
                    secondsRemaining,
                    copy,
                    emergencyActive,
                    activeSession != null ? activeSession.getVehicleId() : null,
                    generateStatusMessage(),
                    cycleNumber,
                    paused
            );
        } finally {
            stateLock.unlock();
        }
    }

    public EmergencyVehicleSession getActiveSession() {
        stateLock.lock();
        try {
            return activeSession;
        } finally {
            stateLock.unlock();
        }
    }

    public boolean isEmergencyActive() {
        stateLock.lock();
        try {
            return emergencyActive;
        } finally {
            stateLock.unlock();
        }
    }

    public Road getActiveRoad() {
        stateLock.lock();
        try {
            return activeRoad;
        } finally {
            stateLock.unlock();
        }
    }

    public TrafficPhaseType getPhaseType() {
        stateLock.lock();
        try {
            return phaseType;
        } finally {
            stateLock.unlock();
        }
    }

    private String generateStatusMessage() {
        if (emergencyActive && activeSession != null) {
            return String.format("EMERGENCY PREEMPTION: %s on %s approach [%s] (%ds)",
                    activeSession.getVehicleId(), activeSession.getRoad(), phaseType, secondsRemaining);
        }
        if (phaseType == TrafficPhaseType.ALL_RED_IDLE) {
            return "IDLE: All 4 signals RED. Awaiting verified ambulance + siren.";
        }
        if (phaseType == TrafficPhaseType.ALL_RED_CLEARANCE) {
            return String.format("Clearance Phase: All signals RED before %s GREEN", nextRoad);
        }
        return String.format("Normal Cycle %d: %s %s (%ds remaining)",
                cycleNumber, activeRoad, phaseType == TrafficPhaseType.GREEN_PHASE ? "GREEN" : "YELLOW", secondsRemaining);
    }

    private void broadcastState(String message) {
        try {
            TrafficStateSnapshot snapshot = getSnapshot();
            messagingTemplate.convertAndSend("/topic/traffic-state", snapshot);
        } catch (Exception e) {
            log.debug("WebSocket broadcast notice: {}", e.getMessage());
        }
    }
}
