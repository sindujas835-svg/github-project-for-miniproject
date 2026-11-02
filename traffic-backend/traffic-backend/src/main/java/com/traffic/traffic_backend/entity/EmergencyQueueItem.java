package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for persistent emergency vehicle queue management in MySQL.
 */
@Entity
@Table(name = "emergency_queue", indexes = {
        @Index(name = "idx_queue_prio_order", columnList = "priority_level, arrival_order"),
        @Index(name = "idx_queue_status", columnList = "status")
})
public class EmergencyQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false, length = 50)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel; // 1 = Critical, 2 = High, 3 = Normal

    @Column(name = "arrival_order", nullable = false)
    private Integer arrivalOrder;

    @Column(nullable = false, length = 50)
    private String status = "QUEUED"; // QUEUED, DISPATCHED, CLEARED, CANCELLED

    @Column(name = "enqueued_at", nullable = false, updatable = false)
    private LocalDateTime enqueuedAt = LocalDateTime.now();

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    public EmergencyQueueItem() {}

    public EmergencyQueueItem(String vehicleId, Road road, Integer priorityLevel, Integer arrivalOrder) {
        this.vehicleId = vehicleId;
        this.road = road;
        this.priorityLevel = priorityLevel;
        this.arrivalOrder = arrivalOrder;
        this.status = "QUEUED";
        this.enqueuedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
    public Integer getArrivalOrder() { return arrivalOrder; }
    public void setArrivalOrder(Integer arrivalOrder) { this.arrivalOrder = arrivalOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(LocalDateTime enqueuedAt) { this.enqueuedAt = enqueuedAt; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
}
