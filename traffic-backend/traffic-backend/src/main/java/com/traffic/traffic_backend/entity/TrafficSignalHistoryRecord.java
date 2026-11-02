package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.SignalColor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity logging each signal transition for audit and analytics.
 */
@Entity
@Table(name = "traffic_signal_history", indexes = {
        @Index(name = "idx_signal_road_time", columnList = "road, recorded_at")
})
public class TrafficSignalHistoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_color", nullable = false, length = 10)
    private SignalColor signalColor;

    @Column(name = "phase_type", nullable = false, length = 50)
    private String phaseType;

    @Column(name = "cycle_number", nullable = false)
    private Long cycleNumber;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "is_emergency_preemption", nullable = false)
    private Boolean isEmergencyPreemption = false;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    public TrafficSignalHistoryRecord() {}

    public TrafficSignalHistoryRecord(Road road, SignalColor signalColor, String phaseType,
                                      Long cycleNumber, Integer durationSeconds, Boolean isEmergencyPreemption) {
        this.road = road;
        this.signalColor = signalColor;
        this.phaseType = phaseType;
        this.cycleNumber = cycleNumber;
        this.durationSeconds = durationSeconds;
        this.isEmergencyPreemption = isEmergencyPreemption;
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public SignalColor getSignalColor() { return signalColor; }
    public void setSignalColor(SignalColor signalColor) { this.signalColor = signalColor; }
    public String getPhaseType() { return phaseType; }
    public void setPhaseType(String phaseType) { this.phaseType = phaseType; }
    public Long getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(Long cycleNumber) { this.cycleNumber = cycleNumber; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Boolean getIsEmergencyPreemption() { return isEmergencyPreemption; }
    public void setIsEmergencyPreemption(Boolean isEmergencyPreemption) { this.isEmergencyPreemption = isEmergencyPreemption; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
