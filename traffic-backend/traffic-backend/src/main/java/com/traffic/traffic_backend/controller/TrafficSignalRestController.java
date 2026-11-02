package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.TrafficConfig;
import com.traffic.traffic_backend.traffic.TrafficSignalController;
import com.traffic.traffic_backend.traffic.TrafficStateSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for real-time traffic signal telemetry, cycle timing configuration,
 * and intersection manual override controls.
 */
@RestController
@RequestMapping("/api/traffic")
@CrossOrigin(origins = "*")
public class TrafficSignalRestController {

    private final TrafficSignalController signalController;
    private final TrafficConfig config;

    public TrafficSignalRestController(TrafficSignalController signalController, TrafficConfig config) {
        this.signalController = signalController;
        this.config = config;
    }

    /**
     * Get current real-time state of the intersection (signals, countdown, active road).
     */
    @GetMapping("/state")
    public ResponseEntity<TrafficStateSnapshot> getState() {
        return ResponseEntity.ok(signalController.getSnapshot());
    }

    /**
     * Get current timing configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<TrafficConfig> getConfig() {
        return ResponseEntity.ok(config);
    }

    /**
     * Dynamically update cycle timings (prototype simulation values).
     */
    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Integer> payload) {
        try {
            if (payload.containsKey("greenIntervalSeconds")) {
                config.setGreenIntervalSeconds(payload.get("greenIntervalSeconds"));
            }
            if (payload.containsKey("yellowIntervalSeconds")) {
                config.setYellowIntervalSeconds(payload.get("yellowIntervalSeconds"));
            }
            if (payload.containsKey("allRedClearanceSeconds")) {
                config.setAllRedClearanceSeconds(payload.get("allRedClearanceSeconds"));
            }
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Traffic timing configuration updated",
                    "config", config
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause() {
        signalController.pause();
        return ResponseEntity.ok(Map.of("status", "paused"));
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume() {
        signalController.resume();
        return ResponseEntity.ok(Map.of("status", "resumed"));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        signalController.resetToNormal();
        return ResponseEntity.ok(Map.of("status", "reset_to_normal"));
    }

    @PostMapping("/test-emergency")
    public ResponseEntity<?> testEmergency(
            @RequestParam(defaultValue = "NORTH") String road,
            @RequestParam(defaultValue = "AMB-DEMO-01") String vehicleId,
            @RequestParam(defaultValue = "1") int priority) {
        try {
            Road targetRoad = Road.valueOf(road.toUpperCase());
            boolean success = signalController.requestEmergencyPreemption(targetRoad, vehicleId, priority);
            return ResponseEntity.ok(Map.of("status", "emergency_requested", "road", targetRoad, "success", success));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid road: " + road));
        }
    }

    @PostMapping("/test-passed")
    public ResponseEntity<?> testPassed(@RequestParam(defaultValue = "AMB-DEMO-01") String vehicleId) {
        signalController.notifyAmbulancePassed(vehicleId);
        return ResponseEntity.ok(Map.of("status", "ambulance_passed_notified", "vehicleId", vehicleId));
    }

    @PostMapping("/test-release")
    public ResponseEntity<?> testRelease() {
        signalController.releaseEmergencyPreemption();
        return ResponseEntity.ok(Map.of("status", "emergency_released"));
    }
}
