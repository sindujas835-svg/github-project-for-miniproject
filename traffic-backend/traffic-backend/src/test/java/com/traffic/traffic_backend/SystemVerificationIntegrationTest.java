package com.traffic.traffic_backend;

import com.traffic.traffic_backend.traffic.TrafficConfig;
import com.traffic.traffic_backend.detection.audio.EmergencySirenAudioService;
import com.traffic.traffic_backend.detection.camera.AmbulanceVisionService;
import com.traffic.traffic_backend.entity.EmergencyQueueItem;
import com.traffic.traffic_backend.entity.EmergencyVerification;
import com.traffic.traffic_backend.repository.*;
import com.traffic.traffic_backend.service.EmergencyQueueService;
import com.traffic.traffic_backend.service.EmergencyVerificationService;
import com.traffic.traffic_backend.service.TelemetryLoggingService;
import com.traffic.traffic_backend.traffic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SystemVerificationIntegrationTest: Verifies the 11 Required Test Scenarios
 * specified in the project requirements.
 */
class SystemVerificationIntegrationTest {

    private TrafficConfig config;
    private TrafficSafetyGuard safetyGuard;
    private SimpMessagingTemplate messagingTemplate;
    private TrafficSignalController controller;

    private AmbulanceVisionService visionService;
    private EmergencySirenAudioService audioService;
    private EmergencyVerificationRepository verificationRepo;
    private EmergencyEventRepository eventRepo;
    private EmergencyQueueItemRepository queueRepo;
    private TelemetryLoggingService telemetryService;

    private EmergencyVerificationService verificationService;
    private EmergencyQueueService queueService;

    @BeforeEach
    void setUp() {
        config = new TrafficConfig();
        config.setGreenIntervalSeconds(8);
        config.setYellowIntervalSeconds(3);
        config.setAllRedClearanceSeconds(2);
        config.setEmergencyYellowSeconds(2);
        config.setEmergencyPreClearanceSeconds(1);
        config.setEmergencyMaxGreenSeconds(15);
        config.setEmergencyPostClearanceSeconds(1);
        config.setEmergencyGracePeriodSeconds(3);

        safetyGuard = new TrafficSafetyGuard();
        messagingTemplate = mock(SimpMessagingTemplate.class);
        controller = new TrafficSignalController(config, safetyGuard, messagingTemplate);

        visionService = mock(AmbulanceVisionService.class);
        audioService = mock(EmergencySirenAudioService.class);
        verificationRepo = mock(EmergencyVerificationRepository.class);
        eventRepo = mock(EmergencyEventRepository.class);
        queueRepo = mock(EmergencyQueueItemRepository.class);
        telemetryService = mock(TelemetryLoggingService.class);

        when(verificationRepo.save(any(EmergencyVerification.class))).thenAnswer(i -> i.getArguments()[0]);
        when(queueRepo.save(any(EmergencyQueueItem.class))).thenAnswer(i -> i.getArguments()[0]);

        queueService = new EmergencyQueueService(
                queueRepo, eventRepo, controller, telemetryService, messagingTemplate
        );
        controller.setClearanceListener(queueService);

        verificationService = new EmergencyVerificationService(
                visionService, audioService, verificationRepo, eventRepo,
                queueService, telemetryService, messagingTemplate
        );
        verificationService.setVerificationWindowMs(3000);
        verificationService.setMinConfidence(0.70f);
    }

    @Test
    @DisplayName("Case 1: Application starts: all signals RED (ALL_RED_IDLE)")
    void testCase01_ApplicationStarts_AllSignalsRed() {
        TrafficStateSnapshot s0 = controller.getSnapshot();
        assertEquals(TrafficPhaseType.ALL_RED_IDLE, s0.getPhaseType());
        assertEquals(0, s0.getSecondsRemaining());
        assertFalse(s0.isEmergencyActive());

        for (Road r : Road.values()) {
            assertEquals(SignalColor.RED, s0.getSignals().get(r), "Road " + r + " must be RED at start");
        }
    }

    @Test
    @DisplayName("Case 2: Ambulance image alone: no emergency")
    void testCase02_AmbulanceImageAlone_NoEmergency() {
        // High confidence camera (0.95), but audio is absent (0.0)
        EmergencyVerification verif = verificationService.verifyDirect(Road.NORTH, "AMB-101", 0.95f, 0.0f, 100);
        assertFalse(verif.getVerified(), "Ambulance camera detection alone must NOT activate emergency");
        assertNotNull(verif.getRejectionReason());
        assertTrue(verif.getRejectionReason().toLowerCase().contains("confidence"));
    }

    @Test
    @DisplayName("Case 3: Siren audio alone: no emergency")
    void testCase03_SirenAudioAlone_NoEmergency() {
        // High confidence siren (0.95), but camera is absent (0.0)
        EmergencyVerification verif = verificationService.verifyDirect(Road.NORTH, "AMB-101", 0.0f, 0.95f, 100);
        assertFalse(verif.getVerified(), "Siren audio detection alone must NOT activate emergency");
        assertNotNull(verif.getRejectionReason());
        assertTrue(verif.getRejectionReason().toLowerCase().contains("confidence"));
    }

    @Test
    @DisplayName("Case 4: Normal vehicle image + horn audio: no emergency")
    void testCase04_NormalVehicleImage_HornAudio_NoEmergency() {
        // Low confidence camera (0.35 = normal vehicle) + low confidence siren (0.40 = car horn)
        EmergencyVerification verif = verificationService.verifyDirect(Road.NORTH, "VEH-99", 0.35f, 0.40f, 100);
        assertFalse(verif.getVerified(), "Normal car and horn must NOT activate emergency");
        assertNotNull(verif.getRejectionReason());
    }

    @Test
    @DisplayName("Case 5: Ambulance + siren on the same road: emergency confirmed")
    void testCase05_AmbulancePlusSirenSameRoad_EmergencyConfirmed() {
        EmergencyVerification verif = verificationService.verifyDirect(Road.NORTH, "AMB-101", 0.95f, 0.92f, 200);
        assertTrue(verif.getVerified(), "Ambulance + Siren on same road must confirm emergency");
        assertNull(verif.getRejectionReason());
    }

    @Test
    @DisplayName("Case 6: Ambulance + siren on different roads: no emergency")
    void testCase06_AmbulancePlusSirenDifferentRoads_NoEmergency() {
        var cam = new com.traffic.traffic_backend.entity.AmbulanceDetection("C1", Road.NORTH, "AMB-1", 0.95f, 0, 0, 0, 0, false);
        var mic = new com.traffic.traffic_backend.entity.SirenDetection("M1", Road.EAST, "SIREN", 0.92f, 950f, 90f, 2, false);

        verificationService.onAmbulanceDetected(cam);
        verificationService.onSirenDetected(mic);

        verify(verificationRepo).save(argThat(v -> !v.getVerified() && v.getRejectionReason().contains("ROAD MISMATCH")));
    }

    @Test
    @DisplayName("Case 7: NORTH emergency: only NORTH GREEN")
    void testCase07_NorthEmergency_OnlyNorthGreen() {
        controller.resetToIdle();
        controller.requestEmergencyPreemption(Road.NORTH, "AMB-NORTH", 1);
        controller.tick(); // Advance pre-clearance (1s)

        TrafficStateSnapshot snap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, snap.getPhaseType());
        assertEquals(Road.NORTH, snap.getActiveRoad());
        assertEquals(SignalColor.GREEN, snap.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.EAST));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.WEST));
    }

    @Test
    @DisplayName("Case 8: SOUTH emergency: only SOUTH GREEN")
    void testCase08_SouthEmergency_OnlySouthGreen() {
        controller.resetToIdle();
        controller.requestEmergencyPreemption(Road.SOUTH, "AMB-SOUTH", 1);
        controller.tick(); // Advance pre-clearance (1s)

        TrafficStateSnapshot snap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, snap.getPhaseType());
        assertEquals(Road.SOUTH, snap.getActiveRoad());
        assertEquals(SignalColor.GREEN, snap.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.EAST));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.WEST));
    }

    @Test
    @DisplayName("Case 9: EAST emergency: only EAST GREEN")
    void testCase09_EastEmergency_OnlyEastGreen() {
        controller.resetToIdle();
        controller.requestEmergencyPreemption(Road.EAST, "AMB-EAST", 1);
        controller.tick(); // Advance pre-clearance (1s)

        TrafficStateSnapshot snap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, snap.getPhaseType());
        assertEquals(Road.EAST, snap.getActiveRoad());
        assertEquals(SignalColor.GREEN, snap.getSignals().get(Road.EAST));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.WEST));
    }

    @Test
    @DisplayName("Case 10: WEST emergency: only WEST GREEN")
    void testCase010_WestEmergency_OnlyWestGreen() {
        controller.resetToIdle();
        controller.requestEmergencyPreemption(Road.WEST, "AMB-WEST", 1);
        controller.tick(); // Advance pre-clearance (1s)

        TrafficStateSnapshot snap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, snap.getPhaseType());
        assertEquals(Road.WEST, snap.getActiveRoad());
        assertEquals(SignalColor.GREEN, snap.getSignals().get(Road.WEST));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.NORTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.SOUTH));
        assertEquals(SignalColor.RED, snap.getSignals().get(Road.EAST));
    }

    @Test
    @DisplayName("Case 11: Emergency ends: all signals RED and remain idle")
    void testCase011_EmergencyEnds_AllSignalsRedAndRemainIdle() {
        controller.resetToIdle();
        controller.requestEmergencyPreemption(Road.NORTH, "AMB-NORTH", 1);
        controller.tick(); // Pre-clearance -> Emergency Green
        assertEquals(TrafficPhaseType.EMERGENCY_GREEN, controller.getSnapshot().getPhaseType());

        // Ambulance departs
        controller.notifyAmbulancePassed("AMB-NORTH");
        assertEquals(TrafficPhaseType.EMERGENCY_POST_CLEARANCE, controller.getSnapshot().getPhaseType());

        // Complete post-clearance (1s)
        controller.tick();

        // System must return to ALL-RED IDLE
        TrafficStateSnapshot idleSnap = controller.getSnapshot();
        assertEquals(TrafficPhaseType.ALL_RED_IDLE, idleSnap.getPhaseType());
        assertFalse(idleSnap.isEmergencyActive());

        for (Road r : Road.values()) {
            assertEquals(SignalColor.RED, idleSnap.getSignals().get(r), "Road " + r + " must be RED after emergency ends");
        }

        // Ticking multiple times does NOT start automatic cycle
        for (int i = 0; i < 5; i++) {
            controller.tick();
            assertEquals(TrafficPhaseType.ALL_RED_IDLE, controller.getSnapshot().getPhaseType(),
                    "System must stay in ALL_RED_IDLE and not cycle");
        }
    }

    @Test
    @DisplayName("Simultaneous multiple ambulances on different roads uses priority queue")
    void testSimultaneousMultipleAmbulancesPriorityQueue() {
        EmergencyQueueItem q1 = queueService.enqueueVehicle("AMB-CRITICAL", Road.NORTH, 1);
        assertEquals("DISPATCHED", q1.getStatus());

        EmergencyQueueItem q2 = queueService.enqueueVehicle("AMB-HIGH", Road.EAST, 2);
        assertEquals("QUEUED", q2.getStatus());
    }

    @Test
    @DisplayName("Strict safety invariant: Zero conflicting greens under concurrent load")
    void testStrictSafetyInvariantZeroConflictingGreens() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean invariantViolated = new AtomicBoolean(false);

        Road[] roads = Road.values();

        for (int i = 0; i < threads; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    for (int step = 0; step < 50; step++) {
                        Road r = roads[(id + step) % 4];
                        controller.requestEmergencyPreemption(r, "CONCURRENT-" + id, 1);
                        controller.tick();

                        long greenCount = controller.getSnapshot().getSignals().values().stream()
                                .filter(c -> c == SignalColor.GREEN)
                                .count();
                        if (greenCount > 1) {
                            invariantViolated.set(true);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertFalse(invariantViolated.get(), "Conflicting green signals detected! Safety invariant broken.");
    }
}
