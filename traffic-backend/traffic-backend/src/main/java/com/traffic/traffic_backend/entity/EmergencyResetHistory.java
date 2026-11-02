package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity recording emergency reset operations in MySQL.
 * Table: emergency_reset_history
 */
@Entity
@Table(name = "emergency_reset_history")
public class EmergencyResetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_id")
    private Long resetId;

    @Column(name = "emergency_id")
    private Long emergencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_road", length = 10)
    private Road selectedRoad;

    @Column(name = "previous_signal_status", length = 50)
    private String previousSignalStatus;

    @Column(name = "reset_timestamp", nullable = false)
    private LocalDateTime resetTimestamp = LocalDateTime.now();

    @Column(name = "reset_status", length = 50)
    private String resetStatus = "RESET_SUCCESS";

    public EmergencyResetHistory() {}

    public EmergencyResetHistory(Long emergencyId, Road selectedRoad, String previousSignalStatus,
                                 LocalDateTime resetTimestamp, String resetStatus) {
        this.emergencyId = emergencyId;
        this.selectedRoad = selectedRoad;
        this.previousSignalStatus = previousSignalStatus;
        this.resetTimestamp = resetTimestamp != null ? resetTimestamp : LocalDateTime.now();
        this.resetStatus = resetStatus != null ? resetStatus : "RESET_SUCCESS";
    }

    public Long getResetId() { return resetId; }
    public void setResetId(Long resetId) { this.resetId = resetId; }

    public Long getEmergencyId() { return emergencyId; }
    public void setEmergencyId(Long emergencyId) { this.emergencyId = emergencyId; }

    public Road getSelectedRoad() { return selectedRoad; }
    public void setSelectedRoad(Road selectedRoad) { this.selectedRoad = selectedRoad; }

    public String getPreviousSignalStatus() { return previousSignalStatus; }
    public void setPreviousSignalStatus(String previousSignalStatus) { this.previousSignalStatus = previousSignalStatus; }

    public LocalDateTime getResetTimestamp() { return resetTimestamp; }
    public void setResetTimestamp(LocalDateTime resetTimestamp) { this.resetTimestamp = resetTimestamp; }

    public String getResetStatus() { return resetStatus; }
    public void setResetStatus(String resetStatus) { this.resetStatus = resetStatus; }
}

