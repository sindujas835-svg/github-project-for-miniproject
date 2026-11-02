package com.traffic.traffic_backend.detection.camera;

import com.traffic.traffic_backend.entity.AmbulanceDetection;

/**
 * Listener interface for downstream components (e.g. EmergencyVerificationService)
 * to receive confirmed ambulance camera detection events.
 */
@FunctionalInterface
public interface AmbulanceDetectionListener {
    void onAmbulanceDetected(AmbulanceDetection detection);
}
