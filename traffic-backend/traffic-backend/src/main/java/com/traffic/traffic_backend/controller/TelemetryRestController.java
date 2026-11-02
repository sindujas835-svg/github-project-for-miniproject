package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.entity.*;
import com.traffic.traffic_backend.service.TelemetryLoggingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API exposing historical telemetry, detection logs, and verification audits from MySQL.
 */
@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryRestController {

    private final TelemetryLoggingService loggingService;

    public TelemetryRestController(TelemetryLoggingService loggingService) {
        this.loggingService = loggingService;
    }

    @GetMapping("/ambulance-detections")
    public ResponseEntity<List<AmbulanceDetection>> getAmbulanceDetections() {
        return ResponseEntity.ok(loggingService.getRecentAmbulanceDetections());
    }

    @GetMapping("/siren-detections")
    public ResponseEntity<List<SirenDetection>> getSirenDetections() {
        return ResponseEntity.ok(loggingService.getRecentSirenDetections());
    }

    @GetMapping("/verifications")
    public ResponseEntity<List<EmergencyVerification>> getVerifications() {
        return ResponseEntity.ok(loggingService.getRecentVerifications());
    }

    @GetMapping("/emergency-events")
    public ResponseEntity<List<EmergencyEvent>> getEmergencyEvents() {
        return ResponseEntity.ok(loggingService.getRecentEvents());
    }

    @GetMapping("/queue")
    public ResponseEntity<List<EmergencyQueueItem>> getActiveQueue() {
        return ResponseEntity.ok(loggingService.getActiveQueue());
    }

    @GetMapping({"/signal-history", "/signals"})
    public ResponseEntity<List<TrafficSignalHistoryRecord>> getSignalHistory() {
        return ResponseEntity.ok(loggingService.getRecentSignalHistory());
    }

    @GetMapping("/system-logs")
    public ResponseEntity<List<SystemLogRecord>> getSystemLogs() {
        return ResponseEntity.ok(loggingService.getRecentSystemLogs());
    }
}
