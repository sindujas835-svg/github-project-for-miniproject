package com.traffic.traffic_backend.detection.audio;

import com.traffic.traffic_backend.entity.SirenDetection;

/**
 * Listener interface for downstream components (e.g. EmergencyVerificationService)
 * to receive confirmed acoustic siren detection events.
 */
@FunctionalInterface
public interface SirenDetectionListener {
    void onSirenDetected(SirenDetection detection);
}
