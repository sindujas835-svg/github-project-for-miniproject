package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.entity.*;
import com.traffic.traffic_backend.repository.*;
import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.SignalColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryLoggingServiceTest {

    private AmbulanceDetectionRepository ambulanceRepo;
    private SirenDetectionRepository sirenRepo;
    private EmergencyVerificationRepository verificationRepo;
    private EmergencyEventRepository eventRepo;
    private EmergencyQueueItemRepository queueRepo;
    private TrafficSignalHistoryRecordRepository historyRepo;
    private SystemLogRecordRepository systemLogRepo;

    private TelemetryLoggingService service;

    @BeforeEach
    void setUp() {
        ambulanceRepo = Mockito.mock(AmbulanceDetectionRepository.class);
        sirenRepo = Mockito.mock(SirenDetectionRepository.class);
        verificationRepo = Mockito.mock(EmergencyVerificationRepository.class);
        eventRepo = Mockito.mock(EmergencyEventRepository.class);
        queueRepo = Mockito.mock(EmergencyQueueItemRepository.class);
        historyRepo = Mockito.mock(TrafficSignalHistoryRecordRepository.class);
        systemLogRepo = Mockito.mock(SystemLogRecordRepository.class);

        service = new TelemetryLoggingService(
                ambulanceRepo, sirenRepo, verificationRepo, eventRepo, queueRepo, historyRepo, systemLogRepo
        );
    }

    @Test
    @DisplayName("Persist vision ambulance detection metadata")
    void testLogAmbulanceDetection() {
        when(ambulanceRepo.save(any(AmbulanceDetection.class))).thenAnswer(i -> i.getArgument(0));

        AmbulanceDetection result = service.logAmbulanceDetection(
                "CAM-NORTH-01", Road.NORTH, "TRK-108", 0.94f, 100, 200, 80, 140, false
        );

        assertNotNull(result);
        assertEquals("CAM-NORTH-01", result.getCameraId());
        assertEquals(Road.NORTH, result.getRoad());
        assertEquals(0.94f, result.getConfidence());
        assertEquals("TRK-108", result.getVehicleTrackId());
        verify(ambulanceRepo).save(any(AmbulanceDetection.class));
    }

    @Test
    @DisplayName("Persist siren acoustic detection metadata")
    void testLogSirenDetection() {
        when(sirenRepo.save(any(SirenDetection.class))).thenAnswer(i -> i.getArgument(0));

        SirenDetection result = service.logSirenDetection(
                "MIC-NORTH-01", Road.NORTH, 0.91f, 960.0f, 78.5f, 3, false
        );

        assertNotNull(result);
        assertEquals("MIC-NORTH-01", result.getMicId());
        assertEquals(Road.NORTH, result.getRoad());
        assertEquals(3, result.getConsecutiveHits());
        verify(sirenRepo).save(any(SirenDetection.class));
    }

    @Test
    @DisplayName("Persist dual verification decision")
    void testLogVerification() {
        when(verificationRepo.save(any(EmergencyVerification.class))).thenAnswer(i -> i.getArgument(0));

        EmergencyVerification result = service.logVerification(
                Road.NORTH, null, null, 120L, true, "Camera and Siren matched on NORTH within 120ms"
        );

        assertNotNull(result);
        assertEquals(Road.NORTH, result.getRoad());
        assertTrue(result.getVerified());
        assertEquals(120L, result.getTimeDeltaMs());
        verify(verificationRepo).save(any(EmergencyVerification.class));
    }

    @Test
    @DisplayName("Persist signal transition history record")
    void testLogSignalChange() {
        when(historyRepo.save(any(TrafficSignalHistoryRecord.class))).thenAnswer(i -> i.getArgument(0));

        TrafficSignalHistoryRecord result = service.logSignalChange(
                Road.NORTH, SignalColor.GREEN, "GREEN_PHASE", 1L, 8, false
        );

        assertNotNull(result);
        assertEquals(Road.NORTH, result.getRoad());
        assertEquals(SignalColor.GREEN, result.getSignalColor());
        verify(historyRepo).save(any(TrafficSignalHistoryRecord.class));
    }

    @Test
    @DisplayName("Persist emergency event lifecycle record")
    void testLogEmergencyEvent() {
        when(eventRepo.save(any(EmergencyEvent.class))).thenAnswer(i -> i.getArgument(0));

        EmergencyEvent result = service.logEmergencyEvent(
                null, "AMB-101", Road.NORTH, "ACTIVE", "DISPATCHED"
        );

        assertNotNull(result);
        assertEquals("AMB-101", result.getVehicleId());
        assertEquals(Road.NORTH, result.getRoad());
        assertEquals("ACTIVE", result.getEventState());
        assertEquals("DISPATCHED", result.getActionTaken());
        verify(eventRepo).save(any(EmergencyEvent.class));
    }

    @Test
    @DisplayName("Persist system audit log record")
    void testLogSystemEvent() {
        when(systemLogRepo.save(any(SystemLogRecord.class))).thenAnswer(i -> i.getArgument(0));

        SystemLogRecord result = service.logSystemEvent("INFO", "TrafficSafetyGuard", "Safety invariants verified");

        assertNotNull(result);
        assertEquals("INFO", result.getLogLevel());
        assertEquals("TrafficSafetyGuard", result.getComponent());
        assertEquals("Safety invariants verified", result.getMessage());
        verify(systemLogRepo).save(any(SystemLogRecord.class));
    }

    @Test
    @DisplayName("Retrieve recent telemetry and audit query methods")
    void testTelemetryQueryMethods() {
        service.getRecentAmbulanceDetections();
        verify(ambulanceRepo).findTop20ByOrderByDetectedAtDesc();

        service.getRecentSirenDetections();
        verify(sirenRepo).findTop20ByOrderByDetectedAtDesc();

        service.getRecentVerifications();
        verify(verificationRepo).findTop20ByOrderByVerifiedAtDesc();

        service.getRecentEvents();
        verify(eventRepo).findTop20ByOrderByStartedAtDesc();

        service.getActiveQueue();
        verify(queueRepo).findByStatusOrderByPriorityLevelAscArrivalOrderAsc("QUEUED");

        service.getRecentSignalHistory();
        verify(historyRepo).findTop50ByOrderByRecordedAtDesc();

        service.getRecentSystemLogs();
        verify(systemLogRepo).findTop50ByOrderByLoggedAtDesc();
    }
}
