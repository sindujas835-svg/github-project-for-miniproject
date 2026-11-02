package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a live camera ambulance detection event.
 */
@Entity
@Table(name = "ambulance_detections", indexes = {
        @Index(name = "idx_amb_road_time", columnList = "road, detected_at"),
        @Index(name = "idx_amb_track", columnList = "vehicle_track_id")
})
public class AmbulanceDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id", nullable = false, length = 50)
    private String cameraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @Column(name = "vehicle_track_id", length = 50)
    private String vehicleTrackId;

    @Column(nullable = false)
    private Float confidence;

    @Column(name = "bbox_x")
    private Integer bboxX;

    @Column(name = "bbox_y")
    private Integer bboxY;

    @Column(name = "bbox_width")
    private Integer bboxWidth;

    @Column(name = "bbox_height")
    private Integer bboxHeight;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated = false;

    @Column(name = "image_filename", length = 255)
    private String imageFilename = "webcam_stream.jpg";

    @Column(name = "ambulance_detected", length = 10)
    private String ambulanceDetected = "YES";

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();

    public AmbulanceDetection() {}

    public AmbulanceDetection(String cameraId, Road road, String vehicleTrackId, Float confidence,
                              Integer bboxX, Integer bboxY, Integer bboxWidth, Integer bboxHeight, Boolean isSimulated) {
        this.cameraId = cameraId;
        this.road = road;
        this.vehicleTrackId = vehicleTrackId;
        this.confidence = confidence;
        this.bboxX = bboxX;
        this.bboxY = bboxY;
        this.bboxWidth = bboxWidth;
        this.bboxHeight = bboxHeight;
        this.isSimulated = isSimulated;
        this.detectedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCameraId() { return cameraId; }
    public void setCameraId(String cameraId) { this.cameraId = cameraId; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public String getVehicleTrackId() { return vehicleTrackId; }
    public void setVehicleTrackId(String vehicleTrackId) { this.vehicleTrackId = vehicleTrackId; }
    public Float getConfidence() { return confidence; }
    public void setConfidence(Float confidence) { this.confidence = confidence; }
    public Integer getBboxX() { return bboxX; }
    public void setBboxX(Integer bboxX) { this.bboxX = bboxX; }
    public Integer getBboxY() { return bboxY; }
    public void setBboxY(Integer bboxY) { this.bboxY = bboxY; }
    public Integer getBboxWidth() { return bboxWidth; }
    public void setBboxWidth(Integer bboxWidth) { this.bboxWidth = bboxWidth; }
    public Integer getBboxHeight() { return bboxHeight; }
    public void setBboxHeight(Integer bboxHeight) { this.bboxHeight = bboxHeight; }
    public Boolean getIsSimulated() { return isSimulated; }
    public void setIsSimulated(Boolean isSimulated) { this.isSimulated = isSimulated; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    public String getImageFilename() { return imageFilename; }
    public void setImageFilename(String imageFilename) { this.imageFilename = imageFilename; }
    public String getAmbulanceDetected() { return ambulanceDetected; }
    public void setAmbulanceDetected(String ambulanceDetected) { this.ambulanceDetected = ambulanceDetected; }
}
