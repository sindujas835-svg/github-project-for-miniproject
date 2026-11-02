
package com.traffic.traffic_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "detection_history")
public class DetectionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ambulance_id", nullable = false)
    private String ambulanceId;

    private String direction;

    @Column(name = "emergency_type")
    private String emergencyType;

    @Column(name = "siren_detected")
    private Boolean sirenDetected = false;

    @Column(name = "signal_action")
    private String signalAction;

    @Column(name = "detected_at", insertable = false, updatable = false)
    private LocalDateTime detectedAt;

    public DetectionHistory() {}

    public Integer getId() { return id; }

    public String getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(String ambulanceId) {
        this.ambulanceId = ambulanceId;
    }

    public String getDirection() { return direction; }
    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) {
        this.emergencyType = emergencyType;
    }

    public Boolean getSirenDetected() { return sirenDetected; }
    public void setSirenDetected(Boolean sirenDetected) {
        this.sirenDetected = sirenDetected;
    }

    public String getSignalAction() { return signalAction; }
    public void setSignalAction(String signalAction) {
        this.signalAction = signalAction;
    }

    public LocalDateTime getDetectedAt() { return detectedAt; }
}
