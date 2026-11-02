package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing an emergency traffic priority preemption lifecycle event.
 */
@Entity
@Table(name = "emergency_events", indexes = {
        @Index(name = "idx_event_road", columnList = "road"),
        @Index(name = "idx_event_vehicle", columnList = "vehicle_id")
})
public class EmergencyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id")
    private EmergencyVerification verification;

    @Column(name = "vehicle_id", nullable = false, length = 50)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @Column(name = "event_state", nullable = false, length = 50)
    private String eventState;

    @Column(name = "action_taken", nullable = false, length = 255)
    private String actionTaken;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    public EmergencyEvent() {}

    public EmergencyEvent(EmergencyVerification verification, String vehicleId, Road road,
                          String eventState, String actionTaken) {
        this.verification = verification;
        this.vehicleId = vehicleId;
        this.road = road;
        this.eventState = eventState;
        this.actionTaken = actionTaken;
        this.startedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public EmergencyVerification getVerification() { return verification; }
    public void setVerification(EmergencyVerification verification) { this.verification = verification; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public String getEventState() { return eventState; }
    public void setEventState(String eventState) { this.eventState = eventState; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getClearedAt() { return clearedAt; }
    public void setClearedAt(LocalDateTime clearedAt) { this.clearedAt = clearedAt; }
}
