package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.detection.audio.EmergencySirenAudioService;
import com.traffic.traffic_backend.detection.audio.SirenDetectionListener;
import com.traffic.traffic_backend.detection.camera.AmbulanceDetectionListener;
import com.traffic.traffic_backend.detection.camera.AmbulanceVisionService;
import com.traffic.traffic_backend.dto.EmergencyVerificationDto;
import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.entity.EmergencyEvent;
import com.traffic.traffic_backend.entity.EmergencyVerification;
import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.repository.EmergencyEventRepository;
import com.traffic.traffic_backend.repository.EmergencyVerificationRepository;
import com.traffic.traffic_backend.traffic.Road;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EmergencyVerificationService: The Core Same-Road Dual-Sensor Decision Engine.
 *
 * CRITICAL SAFETY REQUIREMENT:
 * Emergency priority must be granted ONLY when BOTH:
 * 1. Live camera-based ambulance vision detection
 * 2. Live microphone-based acoustic siren detection
 * are confirmed on the EXACT SAME ROAD within a configurable time window (default: Delta_t <= 3.0s).
 *
 * REJECTION RULES ENFORCED:
 * - If camera sees ambulance, but no siren is heard -> REJECT (No Priority).
 * - If siren is heard, but no ambulance is seen -> REJECT (No Priority).
 * - If camera sees ambulance on NORTH, but siren is heard on EAST -> REJECT (Road Mismatch).
 * - If camera and siren are on the same road, but time difference > 3.0s -> REJECT (Time Window Exceeded).
 * - If confidence is below threshold -> REJECT (Insufficient Confidence).
 */
@Service
public class EmergencyVerificationService implements AmbulanceDetectionListener, SirenDetectionListener {

    private static final Logger log = LoggerFactory.getLogger(EmergencyVerificationService.class);

    private final AmbulanceVisionService visionService;
    private final EmergencySirenAudioService audioService;
    private final EmergencyVerificationRepository verificationRepo;
    private final EmergencyEventRepository eventRepo;
    private final EmergencyQueueService queueService;
    private final TelemetryLoggingService telemetryService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${traffic.emergency.verification-window-ms:3000}")
    private long verificationWindowMs = 3000; // Configurable window (default 3.0 seconds)

    @Value("${traffic.emergency.min-confidence:0.70}")
    private float minConfidence = 0.70f;

    private final ReentrantLock decisionLock = new ReentrantLock();

    // Sliding candidate windows per road
    private final Map<Road, TimestampedDetection<AmbulanceDetection>> recentCameraDetections = new ConcurrentHashMap<>();
    private final Map<Road, TimestampedDetection<SirenDetection>> recentSirenDetections = new ConcurrentHashMap<>();

    private static class TimestampedDetection<T> {
        final T detection;
        final long timestamp;

        TimestampedDetection(T detection, long timestamp) {
            this.detection = detection;
            this.timestamp = timestamp;
        }
    }

    public EmergencyVerificationService(
            AmbulanceVisionService visionService,
            EmergencySirenAudioService audioService,
            EmergencyVerificationRepository verificationRepo,
            EmergencyEventRepository eventRepo,
            EmergencyQueueService queueService,
            TelemetryLoggingService telemetryService,
            SimpMessagingTemplate messagingTemplate) {
        this.visionService = visionService;
        this.audioService = audioService;
        this.verificationRepo = verificationRepo;
        this.eventRepo = eventRepo;
        this.queueService = queueService;
        this.telemetryService = telemetryService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        visionService.addListener(this);
        audioService.addListener(this);
        log.info("EmergencyVerificationService initialized with verification window: {} ms, min confidence: {}",
                verificationWindowMs, minConfidence);
    }

    /**
     * Triggered when camera vision confirms an approaching ambulance.
     */
    @Override
    public void onAmbulanceDetected(AmbulanceDetection cameraDetection) {
        if (cameraDetection == null || cameraDetection.getRoad() == null) return;

        decisionLock.lock();
        try {
            long now = System.currentTimeMillis();
            Road road = cameraDetection.getRoad();
            recentCameraDetections.put(road, new TimestampedDetection<>(cameraDetection, now));

            log.info("VISION SENSOR EVENT: Ambulance on Road {} (Conf: {}, Track: {})",
                    road, cameraDetection.getConfidence(), cameraDetection.getVehicleTrackId());

            evaluateDualVerification(road, cameraDetection, null, now);
        } finally {
            decisionLock.unlock();
        }
    }

    /**
     * Triggered when acoustic microphone confirms an emergency siren.
     */
    @Override
    public void onSirenDetected(SirenDetection sirenDetection) {
        if (sirenDetection == null || sirenDetection.getRoad() == null) return;

        decisionLock.lock();
        try {
            long now = System.currentTimeMillis();
            Road road = sirenDetection.getRoad();
            recentSirenDetections.put(road, new TimestampedDetection<>(sirenDetection, now));

            log.info("AUDIO SENSOR EVENT: Siren on Road {} (Conf: {}, Freq: {} Hz)",
                    road, sirenDetection.getConfidence(), sirenDetection.getFrequencyHz());

            evaluateDualVerification(road, null, sirenDetection, now);
        } finally {
            decisionLock.unlock();
        }
    }

    /**
     * Evaluates whether the current camera and audio detections fulfill the strict same-road dual verification rules.
     */
    private void evaluateDualVerification(
            Road triggeringRoad,
            AmbulanceDetection incomingCamera,
            SirenDetection incomingSiren,
            long now) {

        TimestampedDetection<AmbulanceDetection> camHolder = recentCameraDetections.get(triggeringRoad);
        TimestampedDetection<SirenDetection> sirenHolder = recentSirenDetections.get(triggeringRoad);

        // Check 1: Do we have detections on the SAME ROAD?
        if (camHolder == null || sirenHolder == null) {
            // Check for road mismatch anomaly (e.g. vision on North, but audio on East)
            checkRoadMismatchAnomaly(triggeringRoad, incomingCamera, incomingSiren, now);
            return;
        }

        AmbulanceDetection cam = camHolder.detection;
        SirenDetection siren = sirenHolder.detection;

        long deltaMs = Math.abs(camHolder.timestamp - sirenHolder.timestamp);

        // Check 2: Confidence threshold check
        if (cam.getConfidence() < minConfidence || siren.getConfidence() < minConfidence) {
            String reason = String.format("Insufficient sensor confidence: Vision=%.2f, Audio=%.2f (Min required: %.2f)",
                    cam.getConfidence(), siren.getConfidence(), minConfidence);
            log.warn("DUAL VERIFICATION REJECTED on {}: {}", triggeringRoad, reason);
            recordVerificationDecision(triggeringRoad, cam, siren, deltaMs, false, reason);
            return;
        }

        // Check 3: Time window check (Delta_t <= verificationWindowMs)
        if (deltaMs > verificationWindowMs) {
            String reason = String.format("Time window exceeded: Delta_t=%d ms > %d ms threshold",
                    deltaMs, verificationWindowMs);
            log.warn("DUAL VERIFICATION REJECTED on {}: {}", triggeringRoad, reason);
            recordVerificationDecision(triggeringRoad, cam, siren, deltaMs, false, reason);
            return;
        }

        // ==================== DUAL VERIFICATION SUCCESS ====================
        log.info("DUAL SENSOR VERIFICATION CONFIRMED on Road {}! Delta_t: {} ms (Vision: {}%, Audio: {}%)",
                triggeringRoad, deltaMs,
                String.format("%.1f", cam.getConfidence() * 100),
                String.format("%.1f", siren.getConfidence() * 100));

        EmergencyVerification verification = recordVerificationDecision(
                triggeringRoad, cam, siren, deltaMs, true, null);

        // Create lifecycle event
        String vehicleId = cam.getVehicleTrackId() != null ? cam.getVehicleTrackId() : "AMB-" + triggeringRoad;
        eventRepo.save(new EmergencyEvent(verification, vehicleId, triggeringRoad, "DUAL_VERIFIED",
                "Both camera and siren confirmed on " + triggeringRoad + " (Delta: " + deltaMs + "ms)"));

        telemetryService.logSystemEvent("EMERGENCY_VERIFICATION", "SUCCESS",
                String.format("Dual verification passed on %s for vehicle %s (Delta: %d ms)",
                        triggeringRoad, vehicleId, deltaMs));

        // Consume used detections so they are not reused
        recentCameraDetections.remove(triggeringRoad);
        recentSirenDetections.remove(triggeringRoad);

        // Enqueue into multi-ambulance priority queue
        queueService.enqueueVehicle(vehicleId, triggeringRoad, 1);
    }

    /**
     * Checks if a detection on one road conflicts with a detection on a DIFFERENT road.
     */
    private void checkRoadMismatchAnomaly(
            Road triggeringRoad,
            AmbulanceDetection incomingCamera,
            SirenDetection incomingSiren,
            long now) {

        if (incomingCamera != null) {
            // Camera detected on triggeringRoad. Check if any OTHER road heard a siren recently
            for (Map.Entry<Road, TimestampedDetection<SirenDetection>> entry : recentSirenDetections.entrySet()) {
                Road otherRoad = entry.getKey();
                if (otherRoad != triggeringRoad) {
                    long delta = Math.abs(now - entry.getValue().timestamp);
                    if (delta <= verificationWindowMs) {
                        String reason = String.format("ROAD MISMATCH: Vision on %s, but Siren on %s (Delta: %d ms)",
                                triggeringRoad, otherRoad, delta);
                        log.warn("DUAL VERIFICATION REJECTED: {}", reason);
                        recordVerificationDecision(triggeringRoad, incomingCamera, entry.getValue().detection, delta, false, reason);
                        return;
                    }
                }
            }
            log.info("Awaiting acoustic siren confirmation on Road {} (no preemption granted).", triggeringRoad);
        } else if (incomingSiren != null) {
            // Siren detected on triggeringRoad. Check if any OTHER road saw an ambulance recently
            for (Map.Entry<Road, TimestampedDetection<AmbulanceDetection>> entry : recentCameraDetections.entrySet()) {
                Road otherRoad = entry.getKey();
                if (otherRoad != triggeringRoad) {
                    long delta = Math.abs(now - entry.getValue().timestamp);
                    if (delta <= verificationWindowMs) {
                        String reason = String.format("ROAD MISMATCH: Siren on %s, but Vision on %s (Delta: %d ms)",
                                triggeringRoad, otherRoad, delta);
                        log.warn("DUAL VERIFICATION REJECTED: {}", reason);
                        recordVerificationDecision(triggeringRoad, entry.getValue().detection, incomingSiren, delta, false, reason);
                        return;
                    }
                }
            }
            log.info("Awaiting camera vision confirmation on Road {} (no preemption granted).", triggeringRoad);
        }
    }

    /**
     * Persists verification record to MySQL and broadcasts outcome to WebSocket subscribers.
     */
    private EmergencyVerification recordVerificationDecision(
            Road road,
            AmbulanceDetection cam,
            SirenDetection siren,
            long deltaMs,
            boolean verified,
            String rejectionReason) {

        EmergencyVerification entity = new EmergencyVerification(
                road, cam, siren, deltaMs, verified, rejectionReason
        );
        entity = verificationRepo.save(entity);

        boolean isSim = (cam != null && Boolean.TRUE.equals(cam.getIsSimulated())) ||
                        (siren != null && Boolean.TRUE.equals(siren.getIsSimulated()));

        EmergencyVerificationDto dto = new EmergencyVerificationDto(
                entity.getId(),
                road,
                cam != null ? cam.getId() : null,
                siren != null ? siren.getId() : null,
                cam != null ? cam.getVehicleTrackId() : null,
                cam != null ? cam.getConfidence() : null,
                siren != null ? siren.getConfidence() : null,
                deltaMs,
                verified,
                rejectionReason,
                isSim,
                entity.getVerifiedAt()
        );

        messagingTemplate.convertAndSend("/topic/emergency-verifications", dto);
        return entity;
    }

    /**
     * Direct programmatic verification helper for testing, simulation, and scenario evaluations.
     */
    public EmergencyVerification verifyDirect(Road road, String vehicleId, float camConf, float audioConf, long deltaMs) {
        decisionLock.lock();
        try {
            boolean passesConf = camConf >= minConfidence && audioConf >= minConfidence;
            boolean passesDelta = deltaMs <= verificationWindowMs;
            boolean verified = passesConf && passesDelta;

            String rejectionReason = null;
            if (!passesConf) {
                rejectionReason = String.format("Confidence threshold not met: Cam=%.2f, Audio=%.2f", camConf, audioConf);
            } else if (!passesDelta) {
                rejectionReason = String.format("Time window exceeded: Delta=%d ms > %d ms", deltaMs, verificationWindowMs);
            }

            AmbulanceDetection cam = new AmbulanceDetection("CAM-TEST", road, vehicleId, camConf, 200, 200, 100, 60, true);
            SirenDetection siren = new SirenDetection("MIC-TEST", road, "SIREN", audioConf, 950f, 90f, 2, true);

            EmergencyVerification verif = recordVerificationDecision(road, cam, siren, deltaMs, verified, rejectionReason);
            if (verified) {
                queueService.enqueueVehicle(vehicleId, road, 1);
            }
            return verif;
        } finally {
            decisionLock.unlock();
        }
    }

    public List<EmergencyVerification> getRecentVerifications() {
        return verificationRepo.findTop20ByOrderByVerifiedAtDesc();
    }

    public long getVerificationWindowMs() { return verificationWindowMs; }
    public void setVerificationWindowMs(long ms) { this.verificationWindowMs = ms; }

    public float getMinConfidence() { return minConfidence; }
    public void setMinConfidence(float minConfidence) { this.minConfidence = minConfidence; }

    public void reset() {
        decisionLock.lock();
        try {
            recentCameraDetections.clear();
            recentSirenDetections.clear();
            log.info("EmergencyVerificationService detections cleared on reset.");
        } finally {
            decisionLock.unlock();
        }
    }
}
