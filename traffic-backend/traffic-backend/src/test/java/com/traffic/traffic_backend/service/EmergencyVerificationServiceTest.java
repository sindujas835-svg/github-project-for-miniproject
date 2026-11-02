package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.detection.audio.EmergencySirenAudioService;
import com.traffic.traffic_backend.detection.camera.AmbulanceVisionService;
import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.entity.EmergencyVerification;
import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.repository.EmergencyEventRepository;
import com.traffic.traffic_backend.repository.EmergencyVerificationRepository;
import com.traffic.traffic_backend.traffic.Road;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmergencyVerificationServiceTest {

    private AmbulanceVisionService visionService;
    private EmergencySirenAudioService audioService;
    private EmergencyVerificationRepository verificationRepo;
    private EmergencyEventRepository eventRepo;
    private EmergencyQueueService queueService;
    private TelemetryLoggingService telemetryService;
    private SimpMessagingTemplate messagingTemplate;

    private EmergencyVerificationService verificationService;

    @BeforeEach
    void setUp() {
        visionService = mock(AmbulanceVisionService.class);
        audioService = mock(EmergencySirenAudioService.class);
        verificationRepo = mock(EmergencyVerificationRepository.class);
        eventRepo = mock(EmergencyEventRepository.class);
        queueService = mock(EmergencyQueueService.class);
        telemetryService = mock(TelemetryLoggingService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        when(verificationRepo.save(any(EmergencyVerification.class))).thenAnswer(i -> i.getArguments()[0]);

        verificationService = new EmergencyVerificationService(
                visionService, audioService, verificationRepo, eventRepo,
                queueService, telemetryService, messagingTemplate
        );
        verificationService.setVerificationWindowMs(3000);
        verificationService.setMinConfidence(0.70f);
    }

    @Test
    void testSameRoadDualConfirmationWithinWindow() {
        // Camera detection on NORTH
        AmbulanceDetection cam = new AmbulanceDetection(
                "CAM-01", Road.NORTH, "AMB-101", 0.95f, 200, 200, 100, 60, false
        );
        // Siren detection on NORTH
        SirenDetection siren = new SirenDetection(
                "MIC-01", Road.NORTH, "SIREN", 0.92f, 950f, 90f, 2, false
        );

        verificationService.onAmbulanceDetected(cam);
        verificationService.onSirenDetected(siren);

        // Verification repo must have saved a verified record
        ArgumentCaptor<EmergencyVerification> captor = ArgumentCaptor.forClass(EmergencyVerification.class);
        verify(verificationRepo).save(captor.capture());

        EmergencyVerification recorded = captor.getValue();
        assertTrue(recorded.getVerified(), "Dual verification on same road within window must be confirmed");
        assertEquals(Road.NORTH, recorded.getRoad());
        assertNull(recorded.getRejectionReason());

        // Queue service must be notified
        verify(queueService).enqueueVehicle("AMB-101", Road.NORTH, 1);
    }

    @Test
    void testRoadMismatchRejection() {
        // Camera on NORTH
        AmbulanceDetection cam = new AmbulanceDetection(
                "CAM-01", Road.NORTH, "AMB-101", 0.95f, 200, 200, 100, 60, false
        );
        // Siren on EAST (different road!)
        SirenDetection siren = new SirenDetection(
                "MIC-01", Road.EAST, "SIREN", 0.92f, 950f, 90f, 2, false
        );

        verificationService.onAmbulanceDetected(cam);
        verificationService.onSirenDetected(siren);

        ArgumentCaptor<EmergencyVerification> captor = ArgumentCaptor.forClass(EmergencyVerification.class);
        verify(verificationRepo).save(captor.capture());

        EmergencyVerification recorded = captor.getValue();
        assertFalse(recorded.getVerified(), "Road mismatch must be rejected");
        assertTrue(recorded.getRejectionReason().contains("ROAD MISMATCH"));

        // Queue service must NOT be called for preemption
        verify(queueService, never()).enqueueVehicle(any(), any(), anyInt());
    }

    @Test
    void testCameraOnlyNoSirenRejection() {
        // Camera on NORTH, no siren event occurs
        AmbulanceDetection cam = new AmbulanceDetection(
                "CAM-01", Road.NORTH, "AMB-101", 0.95f, 200, 200, 100, 60, false
        );

        verificationService.onAmbulanceDetected(cam);

        // No verification decision should be saved yet (waiting for siren)
        verify(verificationRepo, never()).save(any());
        verify(queueService, never()).enqueueVehicle(any(), any(), anyInt());
    }

    @Test
    void testLowConfidenceRejection() {
        // Camera on NORTH with low confidence (0.55 < 0.70)
        AmbulanceDetection cam = new AmbulanceDetection(
                "CAM-01", Road.NORTH, "AMB-101", 0.55f, 200, 200, 100, 60, false
        );
        SirenDetection siren = new SirenDetection(
                "MIC-01", Road.NORTH, "SIREN", 0.92f, 950f, 90f, 2, false
        );

        verificationService.onAmbulanceDetected(cam);
        verificationService.onSirenDetected(siren);

        ArgumentCaptor<EmergencyVerification> captor = ArgumentCaptor.forClass(EmergencyVerification.class);
        verify(verificationRepo).save(captor.capture());

        EmergencyVerification recorded = captor.getValue();
        assertFalse(recorded.getVerified(), "Low confidence detection must be rejected");
        assertTrue(recorded.getRejectionReason().contains("Insufficient sensor confidence"));

        verify(queueService, never()).enqueueVehicle(any(), any(), anyInt());
    }
}
