package com.traffic.traffic_backend.dto;

import com.traffic.traffic_backend.traffic.Road;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for queue items displayed in the multi-ambulance dashboard.
 */
public class EmergencyQueueDto {

    private Long id;
    private String vehicleId;
    private Road road;
    private Integer priorityLevel; // 1 = Critical, 2 = High, 3 = Normal
    private Integer arrivalOrder;
    private String status; // QUEUED, DISPATCHED, CLEARED, CANCELLED
    private LocalDateTime enqueuedAt;
    private LocalDateTime dispatchedAt;

    public EmergencyQueueDto() {}

    public EmergencyQueueDto(Long id, String vehicleId, Road road, Integer priorityLevel,
                             Integer arrivalOrder, String status, LocalDateTime enqueuedAt,
                             LocalDateTime dispatchedAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.road = road;
        this.priorityLevel = priorityLevel;
        this.arrivalOrder = arrivalOrder;
        this.status = status;
        this.enqueuedAt = enqueuedAt;
        this.dispatchedAt = dispatchedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
