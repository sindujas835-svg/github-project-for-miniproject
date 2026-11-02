package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.entity.*;
import com.traffic.traffic_backend.repository.*;
import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.SignalColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing persistence of all detection telemetry, verification audits,
 * and signal phase logs to MySQL.
 */
@Service
public class TelemetryLoggingService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryLoggingService.class);

    private final AmbulanceDetectionRepository ambulanceRepo;
    private final SirenDetectionRepository sirenRepo;
    private final EmergencyVerificationRepository verificationRepo;
    private final EmergencyEventRepository eventRepo;
    private final EmergencyQueueItemRepository queueRepo;
    private final TrafficSignalHistoryRecordRepository historyRepo;
    private final SystemLogRecordRepository systemLogRepo;

    public TelemetryLoggingService(
            AmbulanceDetectionRepository ambulanceRepo,
            SirenDetectionRepository sirenRepo,
            EmergencyVerificationRepository verificationRepo,
            EmergencyEventRepository eventRepo,
            EmergencyQueueItemRepository queueRepo,
            TrafficSignalHistoryRecordRepository historyRepo,
            SystemLogRecordRepository systemLogRepo) {
        this.ambulanceRepo = ambulanceRepo;
        this.sirenRepo = sirenRepo;
        this.verificationRepo = verificationRepo;
        this.eventRepo = eventRepo;
        this.queueRepo = queueRepo;
        this.historyRepo = historyRepo;
        this.systemLogRepo = systemLogRepo;
    }

    @Transactional
    public AmbulanceDetection logAmbulanceDetection(String cameraId, Road road, String trackId,
                                                   Float confidence, Integer x, Integer y,
                                                   Integer w, Integer h, Boolean isSimulated) {
        AmbulanceDetection record = new AmbulanceDetection(cameraId, road, trackId, confidence, x, y, w, h, isSimulated);
        return ambulanceRepo.save(record);
    }

    @Transactional
    public SirenDetection logSirenDetection(String micId, Road road, Float confidence,
                                           Float freqHz, Float db, Integer consecutiveHits, Boolean isSimulated) {
        SirenDetection record = new SirenDetection(micId, road, "EMERGENCY_SIREN", confidence, freqHz, db, consecutiveHits, isSimulated);
        return sirenRepo.save(record);
    }

    @Transactional
    public EmergencyVerification logVerification(Road road, AmbulanceDetection amb, SirenDetection siren,
                                                 Long deltaMs, Boolean verified, String reason) {
        EmergencyVerification record = new EmergencyVerification(road, amb, siren, deltaMs, verified, reason);
        return verificationRepo.save(record);
    }

    @Transactional
    public EmergencyEvent logEmergencyEvent(EmergencyVerification verif, String vehicleId, Road road,
                                           String state, String action) {
        EmergencyEvent event = new EmergencyEvent(verif, vehicleId, road, state, action);
        return eventRepo.save(event);
    }

    @Transactional
    public TrafficSignalHistoryRecord logSignalChange(Road road, SignalColor color, String phase,
                                                      Long cycle, Integer durationSec, Boolean isEmergency) {
        TrafficSignalHistoryRecord record = new TrafficSignalHistoryRecord(road, color, phase, cycle, durationSec, isEmergency);
        return historyRepo.save(record);
    }

    @Transactional
    public SystemLogRecord logSystemEvent(String component, String level, String message) {
        String safeLevel = level != null ? (level.length() > 50 ? level.substring(0, 50) : level) : "INFO";
        String safeComp = component != null ? (component.length() > 50 ? component.substring(0, 50) : component) : "SYSTEM";
        SystemLogRecord record = new SystemLogRecord(safeLevel, safeComp, message);
        return systemLogRepo.save(record);
    }

    // Query methods for frontend telemetry
    public List<AmbulanceDetection> getRecentAmbulanceDetections() {
        return ambulanceRepo.findTop20ByOrderByDetectedAtDesc();
    }

    public List<SirenDetection> getRecentSirenDetections() {
        return sirenRepo.findTop20ByOrderByDetectedAtDesc();
    }

    public List<EmergencyVerification> getRecentVerifications() {
        return verificationRepo.findTop20ByOrderByVerifiedAtDesc();
    }

    public List<EmergencyEvent> getRecentEvents() {
        return eventRepo.findTop20ByOrderByStartedAtDesc();
    }

    public List<EmergencyQueueItem> getActiveQueue() {
        return queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("QUEUED");
    }

    public List<TrafficSignalHistoryRecord> getRecentSignalHistory() {
        return historyRepo.findTop50ByOrderByRecordedAtDesc();
    }

    public List<SystemLogRecord> getRecentSystemLogs() {
        return systemLogRepo.findTop50ByOrderByLoggedAtDesc();
    }
}
