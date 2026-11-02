
package com.traffic.traffic_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_requests")
public class EmergencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ambulance_id", nullable = false)
    private String ambulanceId;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "arrival_order", nullable = false)
    private Integer arrivalOrder;

    private String status = "Pending";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public EmergencyRequest() {}

    public Integer getId() { return id; }

    public String getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(String ambulanceId) {
        this.ambulanceId = ambulanceId;
    }

    public String getDirection() { return direction; }
    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getArrivalOrder() { return arrivalOrder; }
    public void setArrivalOrder(Integer arrivalOrder) {
        this.arrivalOrder = arrivalOrder;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
