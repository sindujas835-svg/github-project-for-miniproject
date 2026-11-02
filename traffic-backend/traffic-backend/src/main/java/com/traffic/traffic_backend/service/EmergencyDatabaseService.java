package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.entity.EmergencyHistory;
import com.traffic.traffic_backend.entity.EmergencyResetHistory;
import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.repository.AmbulanceDetectionRepository;
import com.traffic.traffic_backend.repository.EmergencyHistoryRepository;
import com.traffic.traffic_backend.repository.EmergencyResetHistoryRepository;
import com.traffic.traffic_backend.repository.SirenDetectionRepository;
import com.traffic.traffic_backend.traffic.Road;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service managing database audit operations across:
 * - ambulance_detection
 * - siren_detection
 * - emergency_history
 * - emergency_reset_history
 */
@Service
public class EmergencyDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyDatabaseService.class);

    private final AmbulanceDetectionRepository ambulanceRepo;
    private final SirenDetectionRepository sirenRepo;
    private final EmergencyHistoryRepository historyRepo;
    private final EmergencyResetHistoryRepository resetRepo;

    // Track active pending emergency ID in memory
    private volatile Long currentPendingEmergencyId = null;

    public EmergencyDatabaseService(
            AmbulanceDetectionRepository ambulanceRepo,
            SirenDetectionRepository sirenRepo,
            EmergencyHistoryRepository historyRepo,
            EmergencyResetHistoryRepository resetRepo) {
        this.ambulanceRepo = ambulanceRepo;
        this.sirenRepo = sirenRepo;
        this.historyRepo = historyRepo;
        this.resetRepo = resetRepo;
    }

    /**
     * Records an ambulance detection and starts a PENDING emergency in emergency_history.
     */
    @Transactional
    public EmergencyHistory recordAmbulanceDetection(String filename, Road road, boolean detected, float confidence, Long existingDetectionId) {
        String status = detected ? "PENDING" : "NO_AMBULANCE";
        EmergencyHistory history = new EmergencyHistory(
                existingDetectionId,
                null,
                road != null ? road : Road.NORTH,
                confidence,
                null,
                status,
                LocalDateTime.now(),
                null,
                "ALL_RED"
        );

        if (detected) {
            history = historyRepo.save(history);
            this.currentPendingEmergencyId = history.getEmergencyId();
            log.info("Started PENDING Emergency ID: {} on Road: {} (Confidence: {}%)",
                    history.getEmergencyId(), road, String.format("%.1f", confidence * 100));
        }
        return history;
    }

    /**
     * Resolves pending emergency with siren detection outcome.
     */
    @Transactional
    public EmergencyHistory resolvePendingEmergency(Long sirenDetectionId, Road sirenRoad, boolean sirenConfirmed, float sirenConfidence) {
        Optional<EmergencyHistory> pendingOpt = currentPendingEmergencyId != null
                ? historyRepo.findById(currentPendingEmergencyId)
                : historyRepo.findTopByEmergencyStatusOrderByEmergencyStartTimeDesc("PENDING");

        if (pendingOpt.isEmpty()) {
            log.info("No active pending emergency to resolve for siren detection.");
            return null;
        }

        EmergencyHistory history = pendingOpt.get();
        history.setSirenDetectionId(sirenDetectionId);
        history.setSirenConfidence(sirenConfidence);

        boolean sameRoad = (sirenRoad != null && sirenRoad == history.getSelectedRoad());
        boolean highConfidence = (sirenConfirmed && sirenConfidence >= 0.70f && (history.getAmbulanceConfidence() == null || history.getAmbulanceConfidence() >= 0.70f));

        if (sameRoad && highConfidence) {
            history.setEmergencyStatus("CONFIRMED");
            history.setEmergencyConfirmationTime(LocalDateTime.now());
            history.setTrafficSignalStatus(history.getSelectedRoad() + " GREEN");
            log.info("CONFIRMED Emergency ID: {} on Road: {} -> Signal: {}",
                    history.getEmergencyId(), history.getSelectedRoad(), history.getTrafficSignalStatus());
        } else {
            history.setEmergencyStatus("REJECTED");
            history.setTrafficSignalStatus("ALL_RED");
            log.warn("REJECTED Emergency ID: {} (SameRoad={}, HighConf={})",
                    history.getEmergencyId(), sameRoad, highConfidence);
        }

        history = historyRepo.save(history);
        this.currentPendingEmergencyId = null;
        return history;
    }

    /**
     * Records emergency reset in emergency_reset_history.
     */
    @Transactional
    public EmergencyResetHistory recordEmergencyReset(Road currentRoad, String previousSignalStatus) {
        Long activeId = currentPendingEmergencyId;
        if (activeId == null) {
            Optional<EmergencyHistory> last = historyRepo.findTopByEmergencyStatusOrderByEmergencyStartTimeDesc("CONFIRMED");
            if (last.isPresent()) {
                activeId = last.get().getEmergencyId();
            }
        }

        EmergencyResetHistory resetRecord = new EmergencyResetHistory(
                activeId,
                currentRoad != null ? currentRoad : Road.NORTH,
                previousSignalStatus != null ? previousSignalStatus : "ALL_RED",
                LocalDateTime.now(),
                "RESET_SUCCESS"
        );
        resetRecord = resetRepo.save(resetRecord);
        log.info("Recorded Emergency Reset ID: {} for Emergency ID: {} (Previous Signal: {})",
                resetRecord.getResetId(), activeId, previousSignalStatus);

        // Mark any remaining pending emergency as EXPIRED
        if (currentPendingEmergencyId != null) {
            historyRepo.findById(currentPendingEmergencyId).ifPresent(eh -> {
                eh.setEmergencyStatus("EXPIRED");
                eh.setTrafficSignalStatus("ALL_RED");
                historyRepo.save(eh);
            });
            this.currentPendingEmergencyId = null;
        }

        return resetRecord;
    }

    /**
     * Returns top 20 recent records from all four tables for UI demonstration.
     */
    public Map<String, Object> getDatabaseHistory() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ambulanceDetections", ambulanceRepo.findTop20ByOrderByDetectedAtDesc());
        data.put("sirenDetections", sirenRepo.findTop20ByOrderByDetectedAtDesc());
        data.put("emergencyHistory", historyRepo.findTop20ByOrderByEmergencyStartTimeDesc());
        data.put("emergencyResetHistory", resetRepo.findTop20ByOrderByResetTimestampDesc());
        return data;
    }

    public Long getCurrentPendingEmergencyId() {
        return currentPendingEmergencyId;
    }
}

