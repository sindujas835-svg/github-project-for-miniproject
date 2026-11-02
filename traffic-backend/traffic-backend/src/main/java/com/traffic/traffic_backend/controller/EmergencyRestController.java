package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.detection.audio.EmergencySirenAudioService;
import com.traffic.traffic_backend.detection.camera.AmbulanceVisionService;
import com.traffic.traffic_backend.dto.EmergencyQueueDto;
import com.traffic.traffic_backend.entity.EmergencyVerification;
import com.traffic.traffic_backend.service.EmergencyQueueService;
import com.traffic.traffic_backend.service.EmergencyVerificationService;
import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.TrafficSignalController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing dual-verification telemetry, queue management,
 * and emergency trigger / vehicle exit test endpoints.
 */
@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "*")
public class EmergencyRestController {

    private final EmergencyVerificationService verificationService;
    private final EmergencyQueueService queueService;
    private final TrafficSignalController signalController;
    private final AmbulanceVisionService visionService;
    private final EmergencySirenAudioService audioService;
    private final com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService;

    public EmergencyRestController(
            EmergencyVerificationService verificationService,
            EmergencyQueueService queueService,
            TrafficSignalController signalController,
            AmbulanceVisionService visionService,
            EmergencySirenAudioService audioService,
            com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService) {
        this.verificationService = verificationService;
        this.queueService = queueService;
        this.signalController = signalController;
        this.visionService = visionService;
        this.audioService = audioService;
        this.databaseService = databaseService;
    }

    /**
     * Returns the operational status of the emergency priority subsystem.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("emergencyActive", signalController.isEmergencyActive());
        status.put("activeRoad", signalController.getActiveRoad());
        status.put("verificationWindowMs", verificationService.getVerificationWindowMs());
        status.put("minConfidence", verificationService.getMinConfidence());
        status.put("currentlyServicedVehicle", queueService.getCurrentlyServicedVehicleId());
        status.put("activeQueueSize", queueService.getActiveQueue().size());
        return ResponseEntity.ok(status);
    }

    /**
     * Returns the recent dual-sensor verification decisions from MySQL.
     */
    @GetMapping("/verifications")
    public ResponseEntity<List<EmergencyVerification>> getVerifications() {
        return ResponseEntity.ok(verificationService.getRecentVerifications());
    }

    /**
     * Returns the active emergency vehicle priority queue.
     */
    @GetMapping("/queue")
    public ResponseEntity<List<EmergencyQueueDto>> getQueue() {
        return ResponseEntity.ok(queueService.getActiveQueue());
    }

    /**
     * Complete dual-sensor test endpoint: triggers simulated detections and runs them
     * through the full same-road verification pipeline.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerEmergency(
            @RequestParam Road road,
            @RequestParam(required = false, defaultValue = "AMB-101") String vehicleId,
            @RequestParam(required = false, defaultValue = "1") int priorityLevel,
            @RequestParam(required = false, defaultValue = "true") boolean includeCamera,
            @RequestParam(required = false, defaultValue = "true") boolean includeSiren,
            @RequestParam(required = false) Road sirenRoad,
            @RequestParam(required = false, defaultValue = "0.95") float cameraConfidence,
            @RequestParam(required = false, defaultValue = "0.92") float audioConfidence,
            @RequestParam(required = false, defaultValue = "200") long deltaMs) {

        Road targetSirenRoad = sirenRoad != null ? sirenRoad : road;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleId", vehicleId);
        result.put("cameraRoad", road);
        result.put("sirenRoad", targetSirenRoad);
        result.put("includeCamera", includeCamera);
        result.put("includeSiren", includeSiren);
        result.put("deltaMs", deltaMs);

        if (includeCamera) {
            visionService.simulateAmbulanceDetection(road, cameraConfidence, 260, 180, 120, 80);
        }

        if (includeSiren) {
            // If deltaMs > 0, we simulate the time offset
            if (deltaMs > 0) {
                try {
                    Thread.sleep(Math.min(deltaMs, 4000));
                } catch (InterruptedException ignored) {}
            }
            audioService.simulateSirenDetection(targetSirenRoad, audioConfidence, 950f, 90f, 2);
        }

        result.put("status", "DISPATCHED_TO_VERIFICATION_ENGINE");
        result.put("emergencyActiveNow", signalController.isEmergencyActive());
        result.put("queueSizeNow", queueService.getActiveQueue().size());

        return ResponseEntity.ok(result);
    }

    /**
     * Notifies that the ambulance has crossed the intersection exit line.
     */
    @PostMapping("/pass")
    public ResponseEntity<Map<String, Object>> notifyAmbulancePassed(
            @RequestParam(required = false, defaultValue = "AMB-101") String vehicleId) {

        signalController.notifyAmbulancePassed(vehicleId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vehicleId", vehicleId);
        response.put("message", "Exit line notification received. Signal controller initiated post-clearance.");
        response.put("emergencyActive", signalController.isEmergencyActive());
        return ResponseEntity.ok(response);
    }

    /**
     * Resets any active emergency and returns intersection to ALL-RED IDLE mode.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetEmergency() {
        Road currentRoad = signalController.getActiveRoad();
        String prevStatus = signalController.isEmergencyActive() ? currentRoad + " GREEN" : "ALL_RED";

        signalController.resetToIdle();
        verificationService.reset();
        queueService.resetQueue();

        com.traffic.traffic_backend.entity.EmergencyResetHistory resetRecord = databaseService.recordEmergencyReset(currentRoad, prevStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("phaseType", signalController.getPhaseType());
        response.put("emergencyActive", signalController.isEmergencyActive());
        response.put("resetId", resetRecord != null ? resetRecord.getResetId() : null);
        response.put("previousSignalStatus", prevStatus);
        response.put("message", "Emergency reset. All signals returned to RED (ALL_RED_IDLE).");
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the audit history from all four jury-demonstration database tables.
     */
    @GetMapping("/db-history")
    public ResponseEntity<Map<String, Object>> getDatabaseHistory() {
        return ResponseEntity.ok(databaseService.getDatabaseHistory());
    }
}
