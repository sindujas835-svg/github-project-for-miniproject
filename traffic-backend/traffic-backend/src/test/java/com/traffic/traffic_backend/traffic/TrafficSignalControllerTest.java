package com.traffic.traffic_backend.traffic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrafficSignalControllerTest {

    private TrafficConfig config;
    private TrafficSafetyGuard safetyGuard;
    private SimpMessagingTemplate messagingTemplate;
    private TrafficSignalController controller;

    @BeforeEach
    void setUp() {
        // Use short simulation intervals for fast, deterministic unit testing
        config = new TrafficConfig(3, 2, 1);
        config.setEmergencyYellowSeconds(2);
        config.setEmergencyPreClearanceSeconds(1);
        config.setEmergencyPostClearanceSeconds(1);
        safetyGuard = new TrafficSafetyGuard();
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        controller = new TrafficSignalController(config, safetyGuard, messagingTemplate);
    }

    @Test
    @DisplayName("Initial state must be ALL-RED IDLE with all four roads RED")
    void testInitialStateIsAllRedIdle() {
        TrafficStateSnapshot snapshot = controller.getSnapshot();

        assertEquals(TrafficPhaseType.ALL_RED_IDLE, snapshot.getPhaseType());
        assertEquals(0, snapshot.getSecondsRemaining());

        assertEquals(SignalColor.RED, snapshot.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, snapshot.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, snapshot.getSignals().get(Road.EAST));
        assertEquals(SignalColor.RED, snapshot.getSignals().get(Road.WEST));
        assertFalse(snapshot.isEmergencyActive());
    }

    @Test
    @DisplayName("Idle state remains strictly ALL-RED without automatic cycling")
    void testIdleStateDoesNotCycle() {
        for (int i = 0; i < 10; i++) {
            controller.tick();
            TrafficStateSnapshot snapshot = controller.getSnapshot();
            assertEquals(TrafficPhaseType.ALL_RED_IDLE, snapshot.getPhaseType());
            for (Road road : Road.values()) {
                assertEquals(SignalColor.RED, snapshot.getSignals().get(road),
                        "Road " + road + " must remain RED during idle state");
            }
        }
    }

    @Test
    @DisplayName("Emergency Preemption from IDLE: Pre-Clearance (All Red) -> Emergency Green -> Return to ALL-RED IDLE")
    void testEmergencyPreemptionFromIdleSequence() {
        // Initial state is ALL-RED IDLE
        assertEquals(TrafficPhaseType.ALL_RED_IDLE, controller.getSnapshot().getPhaseType());

        // Emergency vehicle AMB-201 arrives on EAST
        boolean accepted = controller.requestEmergencyPreemption(Road.EAST, "AMB-201", 1);
        assertTrue(accepted);

        // Step 1: Pre-clearance flushing intersection (1 second in test config)
        TrafficStateSnapshot preSnap = controller.getSnapshot();
        assertTrue(preSnap.isEmergencyActive());
        assertEquals(TrafficPhaseType.EMERGENCY_PRE_CLEARANCE, preSnap.getPhaseType());
        for (Road road : Road.values()) {
            assertEquals(SignalColor.RED, preSnap.getSignals().get(road),
                    "All roads must be RED during emergency pre-clearance");
        }

        // Step 2: Tick through pre-clearance -> transitions to EMERGENCY_GREEN on EAST
        controller.tick();

        TrafficStateSnapshot greenSnap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, greenSnap.getPhaseType());
        assertEquals(Road.EAST, greenSnap.getActiveRoad());
        assertEquals(SignalColor.GREEN, greenSnap.getSignals().get(Road.EAST));
        assertEquals(SignalColor.RED, greenSnap.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, greenSnap.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, greenSnap.getSignals().get(Road.WEST));

        // Step 3: Ambulance confirmed passed
        controller.notifyAmbulancePassed("AMB-201");
        TrafficStateSnapshot postClearanceSnap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_POST_CLEARANCE, postClearanceSnap.getPhaseType());
        for (Road road : Road.values()) {
            assertEquals(SignalColor.RED, postClearanceSnap.getSignals().get(road),
                    "All roads must be RED during post-clearance interval");
        }

        // Step 4: Tick through post-clearance (1 second) -> returns to ALL-RED IDLE
        controller.tick();
        TrafficStateSnapshot returnedSnap = controller.getSnapshot();
        assertFalse(returnedSnap.isEmergencyActive());
        assertEquals(TrafficPhaseType.ALL_RED_IDLE, returnedSnap.getPhaseType());
        for (Road road : Road.values()) {
            assertEquals(SignalColor.RED, returnedSnap.getSignals().get(road),
                    "All roads must return to RED and remain idle");
        }
    }

    @Test
    @DisplayName("Emergency Preemption for each road verifies only target road is GREEN")
    void testEachRoadEmergencyGreen() {
        for (Road targetRoad : Road.values()) {
            controller.resetToIdle();
            assertEquals(TrafficPhaseType.ALL_RED_IDLE, controller.getSnapshot().getPhaseType());

            controller.requestEmergencyPreemption(targetRoad, "AMB-" + targetRoad, 1);
            controller.tick(); // Complete pre-clearance

            TrafficStateSnapshot snap = controller.getSnapshot();
            assertEquals(TrafficPhaseType.EMERGENCY_GREEN, snap.getPhaseType());
            assertEquals(targetRoad, snap.getActiveRoad());
            assertEquals(SignalColor.GREEN, snap.getSignals().get(targetRoad));

            for (Road otherRoad : Road.values()) {
                if (otherRoad != targetRoad) {
                    assertEquals(SignalColor.RED, snap.getSignals().get(otherRoad),
                            "Other road " + otherRoad + " must be RED while " + targetRoad + " is GREEN");
                }
            }

            controller.notifyAmbulancePassed("AMB-" + targetRoad);
            controller.tick(); // Complete post-clearance
            assertEquals(TrafficPhaseType.ALL_RED_IDLE, controller.getSnapshot().getPhaseType());
        }
    }

    @Test
    @DisplayName("Active emergency session decrements grace period when tracking is lost")
    void testTrackingGracePeriod() {
        controller.requestEmergencyPreemption(Road.NORTH, "AMB-999", 1);
        EmergencyVehicleSession session = controller.getActiveSession();
        assertNotNull(session);
        assertEquals(4, session.getGraceSecondsRemaining());

        // Tracking refresh
        controller.updateVehicleTracking("AMB-999", true);
        assertEquals(4, session.getGraceSecondsRemaining());

        // Grace period decrements when missing
        assertFalse(session.decrementGracePeriod());
        assertEquals(3, session.getGraceSecondsRemaining());
    }
}

