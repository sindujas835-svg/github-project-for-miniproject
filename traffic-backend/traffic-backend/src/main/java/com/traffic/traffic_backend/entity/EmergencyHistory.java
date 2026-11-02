package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing an emergency verification lifecycle in MySQL.
 * Table: emergency_history
 */
@Entity
@Table(name = "emergency_history")
public class EmergencyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emergency_id")
    private Long emergencyId;

    @Column(name = "ambulance_detection_id")
    private Long ambulanceDetectionId;

    @Column(name = "siren_detection_id")
    private Long sirenDetectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_road", nullable = false, length = 10)
    private Road selectedRoad;

    @Column(name = "ambulance_confidence")
    private Float ambulanceConfidence;

    @Column(name = "siren_confidence")
    private Float sirenConfidence;

    @Column(name = "emergency_status", nullable = false, length = 20)
    private String emergencyStatus; // PENDING, CONFIRMED, REJECTED, EXPIRED

    @Column(name = "emergency_start_time", nullable = false)
    private LocalDateTime emergencyStartTime = LocalDateTime.now();

    @Column(name = "emergency_confirmation_time")
    private LocalDateTime emergencyConfirmationTime;

    @Column(name = "traffic_signal_status", length = 50)
    private String trafficSignalStatus = "ALL_RED";

    public EmergencyHistory() {}

    public EmergencyHistory(Long ambulanceDetectionId, Long sirenDetectionId, Road selectedRoad,
                            Float ambulanceConfidence, Float sirenConfidence, String emergencyStatus,
                            LocalDateTime emergencyStartTime, LocalDateTime emergencyConfirmationTime,
                            String trafficSignalStatus) {
        this.ambulanceDetectionId = ambulanceDetectionId;
        this.sirenDetectionId = sirenDetectionId;
        this.selectedRoad = selectedRoad;
        this.ambulanceConfidence = ambulanceConfidence;
        this.sirenConfidence = sirenConfidence;
        this.emergencyStatus = emergencyStatus;
        this.emergencyStartTime = emergencyStartTime != null ? emergencyStartTime : LocalDateTime.now();
        this.emergencyConfirmationTime = emergencyConfirmationTime;
        this.trafficSignalStatus = trafficSignalStatus != null ? trafficSignalStatus : "ALL_RED";
    }

    public Long getEmergencyId() { return emergencyId; }
    public void setEmergencyId(Long emergencyId) { this.emergencyId = emergencyId; }

    public Long getAmbulanceDetectionId() { return ambulanceDetectionId; }
    public void setAmbulanceDetectionId(Long ambulanceDetectionId) { this.ambulanceDetectionId = ambulanceDetectionId; }

    public Long getSirenDetectionId() { return sirenDetectionId; }
    public void setSirenDetectionId(Long sirenDetectionId) { this.sirenDetectionId = sirenDetectionId; }

    public Road getSelectedRoad() { return selectedRoad; }
    public void setSelectedRoad(Road selectedRoad) { this.selectedRoad = selectedRoad; }

    public Float getAmbulanceConfidence() { return ambulanceConfidence; }
    public void setAmbulanceConfidence(Float ambulanceConfidence) { this.ambulanceConfidence = ambulanceConfidence; }

    public Float getSirenConfidence() { return sirenConfidence; }
    public void setSirenConfidence(Float sirenConfidence) { this.sirenConfidence = sirenConfidence; }

    public String getEmergencyStatus() { return emergencyStatus; }
    public void setEmergencyStatus(String emergencyStatus) { this.emergencyStatus = emergencyStatus; }

    public LocalDateTime getEmergencyStartTime() { return emergencyStartTime; }
    public void setEmergencyStartTime(LocalDateTime emergencyStartTime) { this.emergencyStartTime = emergencyStartTime; }

    public LocalDateTime getEmergencyConfirmationTime() { return emergencyConfirmationTime; }
    public void setEmergencyConfirmationTime(LocalDateTime emergencyConfirmationTime) { this.emergencyConfirmationTime = emergencyConfirmationTime; }

    public String getTrafficSignalStatus() { return trafficSignalStatus; }
    public void setTrafficSignalStatus(String trafficSignalStatus) { this.trafficSignalStatus = trafficSignalStatus; }
}

